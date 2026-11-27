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
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * A graph per tenant: in the {@code SCHEMA} multi-tenancy mode, each tenant reads and writes its own graph, named
 * after the tenant, created on first use.
 */
@MicronautTest(transactional = false)
@Property(name = "micronaut.data.multi-tenancy.mode", value = "SCHEMA")
@Property(name = "micronaut.data.age.tenant-graph-prefix", value = "tenant_")
@Property(name = "micronaut.data.age.tenant-graphs.globex", value = "globex_graph")
@Property(name = "micronaut.data.age.tenant-graphs.legacy", value = "tck")
@Property(name = "micronaut.data.age.tenant-graphs.pub", value = "public")
@Property(name = "micronaut.data.age.create-indexes", value = "true")
@Property(name = "micronaut.data.age.create-tenant-graphs", value = "true")
class AgeTenantSpec extends Specification {

    @Inject
    AccountRepository accountRepository

    @Inject
    DataSource dataSource

    @Inject
    TransactionOperations<Connection> transactionOperations

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

    void "a tenant whose graph is the configured graph or a reserved schema is rejected before any statement runs"() {
        given:
        StaticTenantResolver.tenant = tenant

        when:
        accountRepository.findAll()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("graph name [" + graph + "]")

        where:
        tenant   | graph
        "legacy" | "tck"
        "pub"    | "public"
    }

    void "the AGE catalog goes back on the search path after a schema switch on the same connection"() {
        given:
        StaticTenantResolver.tenant = "acme"
        def ada = accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        expect: "a query with a comparison resolves the agtype operator through the search path"
        transactionOperations.executeRead { status ->
            accountRepository.findById(ada.id)
            status.connection.setSchema("public")
            accountRepository.findById(ada.id).get().email
        } == "ada@example.com"
    }

    void "a tenant graph that exists before its first use gets the indexes, outside a transaction"() {
        given: "a graph created by an earlier deployment, before the indexes"
        recreateGraph("tenant_existing")
        StaticTenantResolver.tenant = "existing"

        when:
        accountRepository.findAll()

        then:
        indexes("tenant_existing").containsKey("Account_email_unique")
    }

    void "a tenant graph that exists before its first use gets the indexes, with a transaction"() {
        given:
        recreateGraph("tenant_existing_tx")
        StaticTenantResolver.tenant = "existing_tx"

        when: "the transaction rolls back"
        transactionOperations.executeWrite { status ->
            accountRepository.findAll()
            accountRepository.findAll()
            status.setRollbackOnly()
            null
        }

        then: "so do the indexes"
        !indexes("tenant_existing_tx").containsKey("Account_email_unique")

        when: "the graph is used again"
        accountRepository.save(new Account("ada@example.com", "UK", "London", "ada"))

        then: "the first use was not taken for done"
        indexes("tenant_existing_tx").containsKey("Account_email_unique")

        when:
        accountRepository.save(new Account("ada@example.com", "US", "Shelbyville", "lovelace"))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Account_email_unique")
    }

    void "a tenant graph created in a transaction that rolls back is created again by the next operation"() {
        given:
        recreateGraph("tenant_undone")
        dropGraph("tenant_undone")
        StaticTenantResolver.tenant = "undone"

        when: "a later operation of the transaction sees the graph created under a savepoint"
        transactionOperations.executeWrite { status ->
            accountRepository.findAll()
            accountRepository.findAll()
            status.setRollbackOnly()
            null
        }

        then: "the rollback undid the graph"
        !graphs().contains("tenant_undone")

        when:
        accountRepository.findAll()

        then:
        graphs().contains("tenant_undone")
        indexes("tenant_undone").containsKey("Account_email_unique")
    }

    void "a graph whose indexes cannot be created is not ready until they can"() {
        given: "data that breaks a unique index"
        recreateGraph("tenant_broken")
        cypher("tenant_broken", "CREATE (:Account {email: 'ada@example.com'}), (:Account {email: 'ada@example.com'})")
        StaticTenantResolver.tenant = "broken"

        when: "outside a transaction"
        accountRepository.findAll()

        then:
        thrown(DataAccessException)

        when: "and with one"
        transactionOperations.executeRead { accountRepository.findAll() }

        then:
        thrown(DataAccessException)
        !indexes("tenant_broken").containsKey("Account_email_unique")

        when: "the data is fixed"
        cypher("tenant_broken", "MATCH (a:Account) DETACH DELETE a")
        accountRepository.findAll()

        then:
        indexes("tenant_broken").containsKey("Account_email_unique")
    }

    void "concurrent first writes to a graph that exists do not deadlock on its indexes"() {
        given:
        recreateGraph("tenant_concurrent")
        StaticTenantResolver.tenant = "concurrent"
        def executor = Executors.newFixedThreadPool(4)

        when:
        def saves = (0..<4).collect { i ->
            executor.submit({ accountRepository.save(new Account("user$i@example.com", "UK", "London", "user$i")) } as Callable)
        }
        saves*.get()

        then:
        accountRepository.count() == 4
        indexes("tenant_concurrent").containsKey("Account_email_unique")

        cleanup:
        executor?.shutdownNow()
    }

    private void recreateGraph(String graph) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.prepareStatement("SELECT ag_catalog.drop_graph(name, true) FROM ag_catalog.ag_graph WHERE name = '$graph'").execute()
            connection.prepareStatement("SELECT ag_catalog.create_graph('$graph')").execute()
        }
    }

    private void dropGraph(String graph) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.prepareStatement("SELECT ag_catalog.drop_graph('$graph', true)").execute()
        }
    }

    private void cypher(String graph, String query) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().execute("LOAD 'age'")
            connection.createStatement().execute("SELECT * FROM ag_catalog.cypher('$graph', \$\$ $query \$\$) AS (a ag_catalog.agtype)")
        }
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
