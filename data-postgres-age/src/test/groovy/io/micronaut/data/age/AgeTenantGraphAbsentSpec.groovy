package io.micronaut.data.age

import io.micronaut.context.annotation.Property
import io.micronaut.data.age.features.Account
import io.micronaut.data.age.features.AccountRepository
import io.micronaut.data.age.features.StaticTenantResolver
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

/**
 * By default a tenant id cannot create a graph, which is a schema of the database: the tenant id may come from a request.
 */
@MicronautTest(transactional = false)
@Property(name = "micronaut.data.multi-tenancy.mode", value = "SCHEMA")
@Property(name = "micronaut.data.age.tenant-graph-prefix", value = "absent_")
@Property(name = "micronaut.data.age.create-indexes", value = "true")
class AgeTenantGraphAbsentSpec extends Specification {

    @Inject
    AccountRepository accountRepository

    @Inject
    DataSource dataSource

    @Inject
    TransactionOperations<Connection> transactionOperations

    void cleanup() {
        StaticTenantResolver.tenant = null
    }

    void "a tenant whose graph does not exist fails, and does not create it"() {
        given:
        StaticTenantResolver.tenant = "unknown"

        when: "a read"
        accountRepository.findAll()

        then:
        def read = thrown(DataAccessException)
        read.message.contains("create-tenant-graphs")

        when: "a write"
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        then:
        def write = thrown(DataAccessException)
        write.message.contains("create-tenant-graphs")

        when: "in a transaction"
        transactionOperations.executeWrite { accountRepository.findAll() }

        then:
        def transactional = thrown(DataAccessException)
        transactional.message.contains("create-tenant-graphs")

        and:
        !graphExists("absent_unknown")
    }

    void "a tenant whose graph exists uses it, and there is no need for a tenant to use the configured graph"() {
        given:
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.prepareStatement("SELECT ag_catalog.drop_graph(name, true) FROM ag_catalog.ag_graph WHERE name = 'absent_provisioned'").execute()
            connection.prepareStatement("SELECT ag_catalog.create_graph('absent_provisioned')").execute()
        }

        when:
        StaticTenantResolver.tenant = "provisioned"
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        then:
        accountRepository.findAll()*.email == ["ada@example.com"]

        when: "no tenant"
        StaticTenantResolver.tenant = null

        then: "the configured graph is created as before"
        accountRepository.count() >= 0
    }

    private boolean graphExists(String graph) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.prepareStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = '$graph'").executeQuery().next()
        }
    }
}
