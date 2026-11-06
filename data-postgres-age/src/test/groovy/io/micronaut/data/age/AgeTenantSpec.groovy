package io.micronaut.data.age

import io.micronaut.context.annotation.Property
import io.micronaut.data.age.features.Account
import io.micronaut.data.age.features.AccountRepository
import io.micronaut.data.age.features.StaticTenantResolver
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource

/**
 * A graph per tenant: in the {@code SCHEMA} multi-tenancy mode, each tenant reads and writes its own graph, named
 * after the tenant, created on first use.
 */
@MicronautTest(transactional = false)
@Property(name = "micronaut.data.multi-tenancy.mode", value = "SCHEMA")
@Property(name = "micronaut.data.age.tenant-graph-prefix", value = "tenant_")
@Property(name = "micronaut.data.age.tenant-graphs.globex", value = "globex_graph")
@Property(name = "micronaut.data.age.create-indexes", value = "true")
class AgeTenantSpec extends Specification {

    @Inject
    AccountRepository accountRepository

    @Inject
    DataSource dataSource

    void setup() {
        for (tenant in ["acme", "globex", null]) {
            StaticTenantResolver.tenant = tenant
            accountRepository.deleteAll()
        }
    }

    void cleanup() {
        StaticTenantResolver.tenant = null
    }

    void "each tenant saves to and reads from its own graph"() {
        when:
        StaticTenantResolver.tenant = "acme"
        def ada = accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))
        StaticTenantResolver.tenant = "globex"
        def bob = accountRepository.save(new Account("bob@example.com", "US", "Springfield", "bob"))

        then: "each tenant sees its own account only; graph ids are per graph, so the two accounts share an id"
        accountRepository.findAll()*.email == ["bob@example.com"]
        accountRepository.findById(bob.id).get().email == "bob@example.com"
        accountRepository.count() == 1

        when:
        StaticTenantResolver.tenant = "acme"

        then:
        accountRepository.findAll()*.email == ["ada@example.com"]
        accountRepository.findById(ada.id).get().email == "ada@example.com"
        accountRepository.count() == 1

        when: "no tenant"
        StaticTenantResolver.tenant = null

        then: "the configured graph holds neither"
        accountRepository.findAll().empty

        and: "the graphs are named after the tenants, through the prefix and the map"
        graphs().containsAll(["tenant_acme", "globex_graph"])
        !graphs().contains("tenant_globex")
    }

    void "a tenant graph created on first use has the indexes of the entities"() {
        given:
        StaticTenantResolver.tenant = "acme"
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        when: "the same email in another tenant is a different graph"
        StaticTenantResolver.tenant = "globex"
        accountRepository.save(new Account("ada@example.com", "US", "Springfield", "ada"))

        then:
        accountRepository.count() == 1

        when: "a duplicate in the same tenant hits the unique index of its graph"
        accountRepository.save(new Account("ada@example.com", "US", "Shelbyville", "lovelace"))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Account_email_unique")
        indexes("globex_graph").containsKey("Account_email_unique")
        indexes("tenant_acme").containsKey("Account_email_unique")
    }

    void "a tenant whose graph name is not an identifier is rejected before any statement runs"() {
        given:
        StaticTenantResolver.tenant = "acme; DROP SCHEMA tck CASCADE; --"

        when:
        accountRepository.findAll()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("graph name")
        graphs().contains("tck")
    }

    private Set<String> graphs() {
        query("SELECT name FROM ag_catalog.ag_graph") { it.getString(1) } as Set
    }

    private Map<String, String> indexes(String graph) {
        Map<String, String> indexes = [:]
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            def statement = connection.prepareStatement("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = ?")
            statement.setString(1, graph)
            def resultSet = statement.executeQuery()
            while (resultSet.next()) {
                indexes[resultSet.getString(1)] = resultSet.getString(2)
            }
        }
        indexes
    }

    private <T> List<T> query(String sql, Closure<T> mapper) {
        List<T> values = []
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            def resultSet = connection.prepareStatement(sql).executeQuery()
            while (resultSet.next()) {
                values << mapper(resultSet)
            }
        }
        values
    }
}
