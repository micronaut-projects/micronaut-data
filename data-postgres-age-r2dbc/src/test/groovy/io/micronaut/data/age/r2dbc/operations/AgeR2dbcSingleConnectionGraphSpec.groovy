package io.micronaut.data.age.r2dbc.operations

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Stepwise

import java.time.Duration

/**
 * Checks that the graph is created on the connection of the first operation: the pool holds a single connection,
 * which the operation holds while the graph is created, so a second connection would never be handed out.
 */
@MicronautTest(transactional = false)
@Stepwise
@Property(name = "r2dbc.datasources.default.options.max-size", value = "1")
@Property(name = "r2dbc.datasources.default.options.initial-size", value = "1")
@Property(name = "r2dbc.datasources.default.options.max-acquire-time", value = "PT3S")
@Property(name = "micronaut.data.age.graph", value = "reactive_single_connection")
@Property(name = "micronaut.data.age.create-graph", value = "true")
class AgeR2dbcSingleConnectionGraphSpec extends Specification {

    private static final String QUERY = "RETURN 1 AS one"
    private static final Duration TIMEOUT = Duration.ofSeconds(30)

    @Inject
    ConnectionFactory connectionFactory

    @Inject
    AgeR2dbcRepositoryOperations operations

    @Inject
    ReactorReactiveTransactionOperations<Connection> transactionOperations

    void "a first operation in a transaction creates the graph with the transaction"() {
        given:
        assert connectionFactory.class.name == 'io.r2dbc.pool.ConnectionPool'
        withConnection { Connection connection -> execute(connection, "CREATE EXTENSION IF NOT EXISTS age").then(dropGraph(connection)) }

        when:
        transactionOperations.withTransactionMono { status ->
            operations.reactive().executeQuery(QUERY, [:]).then(Mono.fromRunnable { status.setRollbackOnly() })
        }.block(TIMEOUT)

        then: "the rollback undid the graph"
        !withConnection { Connection connection -> graphExists(connection) }
    }

    void "the next operation creates the graph again"() {
        when:
        operations.reactive().executeQuery(QUERY, [:]).blockLast(TIMEOUT)

        then:
        withConnection { Connection connection -> graphExists(connection) }

        when: "a transaction commits once the graph exists"
        transactionOperations.withTransactionMono { status -> operations.reactive().executeQuery(QUERY, [:]).then() }.block(TIMEOUT)

        then:
        noExceptionThrown()

        cleanup:
        withConnection { Connection connection -> dropGraph(connection) }
    }

    private static Mono<Boolean> graphExists(Connection connection) {
        Flux.from(connection.createStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'reactive_single_connection'").execute())
            .flatMap { it.map { row, metadata -> true } }
            .collectList()
            .map { !it.empty }
    }

    private static Mono<Void> dropGraph(Connection connection) {
        graphExists(connection).flatMap { exists ->
            exists ? execute(connection, "SELECT ag_catalog.drop_graph('reactive_single_connection', true)") : Mono.<Void>empty()
        }
    }

    private static Mono<Void> execute(Connection connection, String sql) {
        Flux.from(connection.createStatement(sql).execute()).concatMap { it.rowsUpdated }.then()
    }

    private <T> T withConnection(Closure<Mono<T>> work) {
        Mono.usingWhen(connectionFactory.create(), work, { Connection connection -> connection.close() }).block(TIMEOUT)
    }
}
