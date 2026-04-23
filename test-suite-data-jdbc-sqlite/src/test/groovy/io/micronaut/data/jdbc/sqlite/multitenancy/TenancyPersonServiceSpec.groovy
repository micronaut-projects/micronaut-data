package io.micronaut.data.jdbc.sqlite.multitenancy

import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification


@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.url", value = "jdbc:sqlite:file:devDb?mode=memory&cache=shared")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Property(name = "datasources.default.dialect", value = "ANSI")
@Property(name = "datasources.default.db-type", value = "sqlite")
@Property(name = "datasources.default.driver-class-name", value = "org.sqlite.JDBC")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.jdbc.sqlite.multitenancy")
@Property(name = "micronaut.data.multi-tenancy.mode", value = "DISCRIMINATOR")
@Property(name = "micronaut.multitenancy.tenantresolver.systemproperty.enabled", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "TenancyPersonServiceSpec")
@MicronautTest(transactional = false)
class TenancyPersonServiceSpec extends Specification {

    @Inject
    TenancyPersonService tenancyPersonService

    @Inject
    @Shared
    JdbcOperations jdbcOperations

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS persons (
            id INT NOT NULL,
            firstName VARCHAR(50) NOT NULL,
            lastName VARCHAR(50) NOT NULL,
            tenantId VARCHAR(10) NOT NULL
        );
    """
    private static final String TENANT_ID = "THE_TENANT"


    void setupSpec() {
        jdbcOperations.execute { connection ->
            def ps = connection.prepareStatement(CREATE_TABLE_SQL)
            ps.execute()
        }
    }

    void setup() {
        System.setProperty("tenantId", TENANT_ID)
    }

    void cleanup() {
        tenancyPersonService.deleteAll()
    }

    def 'save and find'() {
        when:
        def p1 = new TenancyPerson(1, "First", "Last", TENANT_ID)
        tenancyPersonService.save(p1)
        then:
        def foundPerson = tenancyPersonService.findById(1)
        foundPerson.present
    }

    def 'save all and find all'() {
        when:
        def p1 = new TenancyPerson(2, "First", "Last", TENANT_ID)
        def p2 = new TenancyPerson(3, "Second", "Last", TENANT_ID)
        def people = List.of(p1, p2)
        tenancyPersonService.saveAll(people)
        def result = tenancyPersonService.findAll()
        then:
        result
        result.size() == 2
    }

    def 'insert with query and find all'() {
        when:
        def p1 = new TenancyPerson(4, "First", "Last", TENANT_ID)
        def p2 = new TenancyPerson(5, "Second","Last", TENANT_ID)
        def people = List.of(p1, p2)
        def insertCount = tenancyPersonService.insertWithQuery(people)
        def result = tenancyPersonService.findAll()
        then:
        insertCount == 2
        result
        result.size() == 2
    }

    def 'insert with query single insert and find all'() {
        when:
        def p1 = new TenancyPerson(6, "First", "Last", TENANT_ID)
        def p2 = new TenancyPerson(7, "Second", "Last", TENANT_ID)
        tenancyPersonService.insertWithQuerySingle(p1)
        tenancyPersonService.insertWithQuerySingle(p2)
        def result = tenancyPersonService.findAll()
        then:
        result
        result.size() == 2
    }

    def 'insert with query the long way and find all'() {
        when:
        def p1 = new TenancyPerson(8, "First", "Last", TENANT_ID)
        def p2 = new TenancyPerson(9, "Second", "Last", TENANT_ID)
        def people = List.of(p1, p2)
        tenancyPersonService.insertWithQueryTheLongWay(people)
        def result = tenancyPersonService.findAll()
        then:
        result
        result.size() == 2
    }
}
