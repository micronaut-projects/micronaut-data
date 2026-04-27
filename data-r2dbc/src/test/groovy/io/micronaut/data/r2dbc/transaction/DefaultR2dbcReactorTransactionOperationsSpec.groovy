package io.micronaut.data.r2dbc.transaction

import io.micronaut.data.connection.annotation.TransactionPriority
import io.micronaut.data.connection.reactive.ReactiveConnectionStatus
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionMetadata
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class DefaultR2dbcReactorTransactionOperationsSpec extends Specification {

    void "does not evaluate oracle session priority when transaction priority is absent"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null)
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def definition = new DefaultTransactionDefinition()

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()

        then:
        1 * connectionStatus.getConnection() >> connection
        1 * connection.beginTransaction() >> Mono.empty()
        0 * connection.getMetadata()
        0 * connectionStatus.registerReactiveSynchronization(_)
    }

    void "applies and resets oracle session priority when transaction priority is present"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null)
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def metadata = Mock(ConnectionMetadata)
        def applyStatement = Mock(Statement)
        def applyResult = Mock(Result)
        def resetStatement = Mock(Statement)
        def resetResult = Mock(Result)
        def definition = new DefaultTransactionDefinition()
        definition.setPriority(TransactionPriority.Level.LOW)
        ReactiveConnectionSynchronization synchronization = null

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()

        then:
        1 * connectionStatus.getConnection() >> connection
        1 * connection.getMetadata() >> metadata
        1 * metadata.getDatabaseProductName() >> "Oracle"
        1 * connection.createStatement('ALTER SESSION SET "txn_priority"="LOW"') >> applyStatement
        1 * applyStatement.execute() >> Flux.just(applyResult)
        1 * applyResult.getRowsUpdated() >> Mono.just(0L)
        1 * connectionStatus.registerReactiveSynchronization(_ as ReactiveConnectionSynchronization) >> { args ->
            synchronization = (ReactiveConnectionSynchronization) args[0]
        }
        1 * connection.beginTransaction() >> Mono.empty()

        when:
        Mono.from(synchronization.onError(new RuntimeException("boom"))).block()

        then:
        1 * connection.createStatement('ALTER SESSION SET "txn_priority"="HIGH"') >> resetStatement
        1 * resetStatement.execute() >> Flux.just(resetResult)
        1 * resetResult.getRowsUpdated() >> Mono.just(0L)
    }

    void "does not apply oracle session priority when db is not oracle"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null)
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def metadata = Mock(ConnectionMetadata)
        def definition = new DefaultTransactionDefinition()
        definition.setPriority(TransactionPriority.Level.LOW)

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()

        then:
        1 * connectionStatus.getConnection() >> connection
        1 * connection.getMetadata() >> metadata
        1 * metadata.getDatabaseProductName() >> "PostgreSQL"
        1 * connection.beginTransaction() >> Mono.empty()
        0 * connection.createStatement(_)
        0 * connectionStatus.registerReactiveSynchronization(_)
    }
}
