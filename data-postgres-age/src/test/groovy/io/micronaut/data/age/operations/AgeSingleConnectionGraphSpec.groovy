package io.micronaut.data.age.operations

import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Stepwise

import javax.sql.DataSource
import java.sql.Connection

/**
 * Checks that the graph is created on the connection of the first operation: the pool holds a single connection,
 * which the operation holds while the graph is created, so a second connection would never be handed out.
 */
@MicronautTest(transactional = false)
@Stepwise
@Property(name = "datasources.default.maximum-pool-size", value = "1")
@Property(name = "datasources.default.minimum-idle", value = "1")
@Property(name = "datasources.default.connection-timeout", value = "3000")
@Property(name = "micronaut.data.age.graph", value = "single_connection")
@Property(name = "micronaut.data.age.create-graph", value = "true")
class AgeSingleConnectionGraphSpec extends Specification {

    private static final String QUERY = "RETURN 1 AS one"

    @Inject
    DataSource dataSource

    @Inject
    AgeRepositoryOperations operations

    @Inject
    TransactionOperations<Connection> transactionOperations

    void "a first operation in a transaction creates the graph with the transaction"() {
        given:
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { it.execute("CREATE EXTENSION IF NOT EXISTS age") }
            dropGraph(connection)
        }

        when:
        transactionOperations.executeWrite { status ->
            operations.executeQuery(QUERY, [:])
            status.setRollbackOnly()
            null
        }

        then: "the rollback undid the graph"
        !withConnection { Connection connection -> graphExists(connection) }
    }

    void "the next operation creates the graph again"() {
        when:
        operations.executeQuery(QUERY, [:])

        then:
        withConnection { Connection connection -> graphExists(connection) }

        when: "a transaction commits once the graph exists"
        transactionOperations.executeWrite { status -> operations.executeQuery(QUERY, [:]) }

        then:
        noExceptionThrown()

        cleanup:
        withConnection { Connection connection -> dropGraph(connection) }
    }

    private static boolean graphExists(Connection connection) {
        connection.prepareStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'single_connection'").withCloseable { statement ->
            statement.executeQuery().withCloseable { it.next() }
        }
    }

    private static void dropGraph(Connection connection) {
        if (graphExists(connection)) {
            connection.createStatement().withCloseable { it.execute("SELECT ag_catalog.drop_graph('single_connection', true)") }
        }
    }

    private <T> T withConnection(Closure<T> work) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { Connection connection ->
            connection.autoCommit = true
            work(connection)
        }
    }
}
