package io.micronaut.data.jdbc.oraclexe.jsonviewschema

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.test.support.TestPropertyProviderFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

/**
 * A duality view with its tables and its join table in a schema that is not the application user's.
 */
@TestResourcesScope("oracle-jsonview-schema")
class OracleJdbcJsonViewSchemaSpec extends Specification {

    private static final Map<String, String> DATASOURCE = [
            'datasources.default.db-type' : 'oracle',
            'datasources.default.dialect' : 'ORACLE',
            'datasources.default.packages': 'io.micronaut.data.jdbc.oraclexe.jsonviewschema'
    ]

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    LibraryShelfViewRepository shelfViewRepository

    @Shared
    DataSource dataSource

    def setupSpec() {
        Map<String, String> testResources = testResourcesProperties()
        grantSchemaPrivileges(testResources)
        context = ApplicationContext.run(testResources + DATASOURCE + ['datasources.default.schema-generate': 'CREATE_DROP'], Environment.TEST)
        shelfViewRepository = context.getBean(LibraryShelfViewRepository)
        dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
    }

    void "the view, its tables and its join table are named with their schema and quoted"() {
        when:
        String[] sql = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(new RuntimePersistentEntity(LibraryShelfView))

        then:
        sql[0] == "CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW \"LIBRARY\".\"LIBRARY_SHELF_VIEW\" AS SELECT JSON {'_id': lsh.\"ID\", 'comment': lsh.\"COMMENT\", " +
                "'books': [SELECT JSON {UNNEST (SELECT JSON {'id': lbk.\"ID\", 'title': lbk.\"TITLE\"} FROM \"LIBRARY\".\"TBL_LIB_BOOK\" lbk WITH UPDATE INSERT  " +
                "WHERE \"SHELF-LINKS\".\"LIBRARY_BOOK_ID\"=lbk.\"ID\")} " +
                "FROM \"LIBRARY\".\"SHELF-LINKS\" WITH UPDATE INSERT DELETE WHERE lsh.\"ID\"=\"SHELF-LINKS\".\"LIBRARY_SHELF_ID\"]} " +
                "FROM \"LIBRARY\".\"TBL_LIB_SHELF\" lsh WITH UPDATE INSERT DELETE "
    }

    void "schema generation creates the view in the other schema"() {
        expect:
        countRows("SELECT COUNT(*) FROM all_objects WHERE owner = 'LIBRARY' AND object_name = 'LIBRARY_SHELF_VIEW' AND object_type = 'VIEW'") == 1
    }

    void "a shelf is written, read and deleted through the view in the other schema"() {
        given:
        def shelfView = new LibraryShelfView()
        shelfView.setComment("Classics")
        shelfViewRepository.save(shelfView)
        shelfView.setBooks([new LibraryBookSubView(1, "Dune"), new LibraryBookSubView(2, "Emma")])
        String links = "SELECT COUNT(*) FROM LIBRARY.\"SHELF-LINKS\" WHERE library_shelf_id = " + shelfView.getId()

        when:
        shelfViewRepository.update(shelfView)
        def result = shelfViewRepository.findById(shelfView.getId())

        then:
        result.present
        result.get().comment == "Classics"
        result.get().books.collect { it.title() }.sort() == ["Dune", "Emma"]
        countRows(links) == 2

        when: "a book is taken off the shelf"
        shelfView.setBooks([new LibraryBookSubView(2, "Emma")])
        shelfViewRepository.update(shelfView)

        then: "its join table row is deleted and the book is kept"
        shelfViewRepository.findById(shelfView.getId()).get().books.collect { it.title() } == ["Emma"]
        countRows(links) == 1
        countRows("SELECT COUNT(*) FROM LIBRARY.TBL_LIB_BOOK WHERE id IN (1, 2)") == 2

        when: "the shelf is deleted through the view"
        shelfViewRepository.deleteById(shelfView.getId())

        then: "its join table rows are deleted and the books are kept"
        !shelfViewRepository.findById(shelfView.getId()).present
        countRows(links) == 0
        countRows("SELECT COUNT(*) FROM LIBRARY.TBL_LIB_BOOK WHERE id IN (1, 2)") == 2
    }

    private long countRows(String sql) {
        try (Connection connection = dataSource.connection;
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next()
            return resultSet.getLong(1)
        }
    }

    /**
     * Schema generation creates the LIBRARY schema and the tables and the view in it, which needs system privileges that
     * the application user doesn't have. Test Resources starts Oracle Free with the same password for SYSTEM and the
     * application user, which lets the spec grant them.
     */
    private static void grantSchemaPrivileges(Map<String, String> testResources) {
        ApplicationContext context = ApplicationContext.run(testResources + DATASOURCE + ['datasources.default.schema-generate': 'NONE'], Environment.TEST)
        try {
            String url = context.getRequiredProperty('datasources.default.url', String)
            String username = context.getRequiredProperty('datasources.default.username', String)
            String password = context.getRequiredProperty('datasources.default.password', String)
            try (Connection connection = DriverManager.getConnection(url, 'system', password);
                 Statement statement = connection.createStatement()) {
                statement.execute("""GRANT CREATE USER, CREATE ANY TABLE, DROP ANY TABLE, CREATE ANY INDEX, CREATE ANY SEQUENCE, DROP ANY SEQUENCE,
                    SELECT ANY SEQUENCE, CREATE ANY VIEW, DROP ANY VIEW, SELECT ANY TABLE, INSERT ANY TABLE, UPDATE ANY TABLE,
                    DELETE ANY TABLE, LOCK ANY TABLE TO $username""".toString())
            }
        } finally {
            context.close()
        }
    }

    private Map<String, String> testResourcesProperties() {
        Map<String, String> properties = [:]
        ServiceLoader.load(TestPropertyProviderFactory).stream()
                .forEach { properties.putAll(it.get().create([:], this.class).get()) }
        return properties
    }
}
