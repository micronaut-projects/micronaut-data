package io.micronaut.data.jdbc.contributor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.config.JdbcSchemaContributor
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.runtime.schema.SchemaContext
import jakarta.inject.Singleton
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

/**
 * With create-drop, a restart replaces the objects of contributors: a domain the tables use, and a table and a view of
 * two contributors where the later one's view depends on the earlier one's table.
 */
class H2SchemaContributorRestartSpec extends Specification {

    void "a restart with create-drop recreates the domains the tables use and the objects of several contributors"() {
        given:
        String url = "jdbc:h2:mem:contributorRestart${System.nanoTime()};DB_CLOSE_DELAY=-1"

        when: "the first start creates the domain with 5 characters and a note"
        ApplicationContext first = start(url, 5)
        execute(first, "INSERT INTO restart_note(text) VALUES ('before the restart')")
        first.close()

        and: "the second start asks for 320 characters"
        ApplicationContext second = start(url, 320)
        execute(second, "INSERT INTO restart_contact(email) VALUES ('ged@roke.example')")

        then: "the domain and its column were recreated, and the note table under the view was dropped"
        count(second, "SELECT COUNT(*) FROM restart_contact") == 1
        count(second, "SELECT COUNT(*) FROM restart_note_texts") == 0

        cleanup:
        second?.close()
    }

    void "the domain is too small without the restart"() {
        given:
        ApplicationContext context = start("jdbc:h2:mem:contributorRestartSmall${System.nanoTime()};DB_CLOSE_DELAY=-1", 5)

        when:
        execute(context, "INSERT INTO restart_contact(email) VALUES ('ged@roke.example')")

        then:
        thrown(SQLException)

        cleanup:
        context.close()
    }

    private static ApplicationContext start(String url, int emailSize) {
        return ApplicationContext.run([
            'spec.name'                          : 'H2SchemaContributorRestartSpec',
            'spec.email-size'                    : emailSize,
            'datasources.default.url'            : url,
            'datasources.default.driver-class-name': 'org.h2.Driver',
            'datasources.default.username'       : 'sa',
            'datasources.default.password'       : '',
            'datasources.default.dialect'        : 'H2',
            'datasources.default.schema-generate': 'CREATE_DROP',
            'datasources.default.packages'       : 'io.micronaut.data.jdbc.contributor'
        ])
    }

    private static void execute(ApplicationContext context, String sql) {
        DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection.withCloseable { Connection connection ->
            connection.createStatement().withCloseable { it.executeUpdate(sql) }
        }
    }

    private static int count(ApplicationContext context, String sql) {
        DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection.withCloseable { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery(sql).withCloseable { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
            }
        }
    }

    /**
     * The domain of the email column, whose size changes between starts.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'H2SchemaContributorRestartSpec')
    static class DomainContributor implements JdbcSchemaContributor {

        final int size

        DomainContributor(@Property(name = 'spec.email-size') int size) {
            this.size = size
        }

        @Override
        List<String> buildBeforeTablesDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["DROP DOMAIN IF EXISTS restart_email"]
        }

        @Override
        List<String> buildBeforeTablesStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["CREATE DOMAIN restart_email AS VARCHAR(${size})".toString()]
        }
    }

    /**
     * A table of its own, which a later contributor's view depends on.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'H2SchemaContributorRestartSpec')
    static class NoteTableContributor implements JdbcSchemaContributor {

        @Override
        int getOrder() {
            return 0
        }

        @Override
        List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["CREATE TABLE restart_note (id BIGINT AUTO_INCREMENT PRIMARY KEY, text VARCHAR(100))"]
        }

        @Override
        List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["DROP TABLE IF EXISTS restart_note"]
        }
    }

    /**
     * A view over the note table of the earlier contributor.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'H2SchemaContributorRestartSpec')
    static class NoteViewContributor implements JdbcSchemaContributor {

        @Override
        int getOrder() {
            return 10
        }

        @Override
        List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["CREATE VIEW restart_note_texts AS SELECT text FROM restart_note"]
        }

        @Override
        List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["DROP VIEW IF EXISTS restart_note_texts"]
        }
    }
}

@MappedEntity("restart_contact")
class RestartContact {
    @Id
    @GeneratedValue
    Long id

    @MappedProperty(definition = "restart_email")
    String email
}
