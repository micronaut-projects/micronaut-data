package io.micronaut.data.r2dbc.transaction

import io.micronaut.data.connection.reactive.ReactiveConnectionStatus
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.micronaut.transaction.support.OracleTransactionPriorityStatements
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionMetadata
import io.r2dbc.spi.R2dbcException
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class DefaultR2dbcReactorTransactionOperationsSpec extends Specification {

    void "does not evaluate oracle session priority when transaction priority is absent"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null, oracleListeners())
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

    void "applies oracle session priority and restores the previous priority when the transaction is committed"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null, oracleListeners())
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def resetStatement = Mock(Statement)
        def resetResult = Mock(Result)
        def definition = priority(OracleTransactional.Priority.LOW)
        connectionStatus.getConnection() >> connection
        oracleConnection(connection, Optional.of("MEDIUM"))

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()

        then: "the synchronization is bound to the transaction, not to the connection"
        0 * connectionStatus.registerReactiveSynchronization(_)
        1 * connection.beginTransaction() >> Mono.empty()

        when:
        Mono.from(transactionOperations.commitTransaction(connectionStatus, definition)).block()

        then:
        1 * connection.commitTransaction() >> Mono.empty()
        1 * connection.createStatement('ALTER SESSION SET "txn_priority"="MEDIUM"') >> resetStatement
        1 * resetStatement.execute() >> Flux.just(resetResult)
        1 * resetResult.getRowsUpdated() >> Mono.just(0L)

        when: "completed again"
        Mono.from(transactionOperations.rollbackTransaction(connectionStatus, definition)).block()

        then: "not reset again"
        1 * connection.rollbackTransaction() >> Mono.empty()
        0 * connection.createStatement(_)
    }

    void "resets oracle session priority to HIGH when the previous priority is unknown"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null, oracleListeners())
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def resetStatement = Mock(Statement)
        def resetResult = Mock(Result)
        def definition = priority(OracleTransactional.Priority.LOW)
        connectionStatus.getConnection() >> connection
        oracleConnection(connection, Optional.empty())
        connection.beginTransaction() >> Mono.empty()

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()
        Mono.from(transactionOperations.rollbackTransaction(connectionStatus, definition)).block()

        then:
        1 * connection.rollbackTransaction() >> Mono.empty()
        1 * connection.createStatement('ALTER SESSION SET "txn_priority"="HIGH"') >> resetStatement
        1 * resetStatement.execute() >> Flux.just(resetResult)
        1 * resetResult.getRowsUpdated() >> Mono.just(0L)
    }

    void "restores oracle session priority when the commit fails"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null, oracleListeners())
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def resetStatement = Mock(Statement)
        def resetResult = Mock(Result)
        def definition = priority(OracleTransactional.Priority.LOW)
        connectionStatus.getConnection() >> connection
        oracleConnection(connection, Optional.of("MEDIUM"))
        connection.beginTransaction() >> Mono.empty()

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()
        Mono.from(transactionOperations.commitTransaction(connectionStatus, definition)).block()

        then:
        def e = thrown(IllegalStateException)
        e.message == "commit failed"
        1 * connection.commitTransaction() >> Mono.error(new IllegalStateException("commit failed"))
        1 * connection.createStatement('ALTER SESSION SET "txn_priority"="MEDIUM"') >> resetStatement
        1 * resetStatement.execute() >> Flux.just(resetResult)
        1 * resetResult.getRowsUpdated() >> Mono.just(0L)
    }

    void "restores oracle session priority after the rollback when the commit fails with a priority rollback"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null, oracleListeners())
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def resetStatement = Mock(Statement)
        def resetResult = Mock(Result)
        def definition = priority(OracleTransactional.Priority.LOW)
        connectionStatus.getConnection() >> connection
        oracleConnection(connection, Optional.of("MEDIUM"))
        connection.beginTransaction() >> Mono.empty()
        def priorityRollback = new R2dbcException("ORA-63302: transaction must be rolled back", "99999", 63302) {}

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()
        Mono.from(transactionOperations.commitTransaction(connectionStatus, definition)).block()

        then: "the session accepts statements only after the rollback"
        thrown(R2dbcException)
        1 * connection.commitTransaction() >> Mono.error(priorityRollback)
        0 * connection.createStatement({ String sql -> sql.startsWith('ALTER SESSION') })

        when:
        Mono.from(transactionOperations.rollbackTransaction(connectionStatus, definition)).block()

        then:
        1 * connection.rollbackTransaction() >> Mono.empty()
        1 * connection.createStatement('ALTER SESSION SET "txn_priority"="MEDIUM"') >> resetStatement
        1 * resetStatement.execute() >> Flux.just(resetResult)
        1 * resetResult.getRowsUpdated() >> Mono.just(0L)
    }

    void "does not apply oracle session priority when db is not oracle"() {
        given:
        def transactionOperations = new DefaultR2dbcReactorTransactionOperations("default", null, oracleListeners())
        def connectionStatus = Mock(ReactiveConnectionStatus<Connection>)
        def connection = Mock(Connection)
        def metadata = Mock(ConnectionMetadata)
        def definition = new DefaultTransactionDefinition()
        definition.putProperty(OracleTransactional.ORACLE_PRIORITY, "invalid")

        when:
        Mono.from(transactionOperations.beginTransaction(connectionStatus, definition)).block()

        then:
        2 * connectionStatus.getConnection() >> connection
        1 * connection.getMetadata() >> metadata
        1 * metadata.getDatabaseProductName() >> "PostgreSQL"
        1 * connection.beginTransaction() >> Mono.empty()
        0 * connection.createStatement(_)
        0 * connectionStatus.registerReactiveSynchronization(_)
        noExceptionThrown()
    }

    void "recognizes priority rollback only from an Oracle R2DBC connection"() {
        given:
        def connection = Mock(Connection)
        def metadata = Mock(ConnectionMetadata)
        def exception = new R2dbcException("ORA-63302: transaction must be rolled back", "99999", 63302) {}

        when:
        def result = DefaultR2dbcReactorTransactionOperations.isOraclePriorityRollback(connection, exception)

        then:
        1 * connection.getMetadata() >> metadata
        1 * metadata.getDatabaseProductName() >> "Oracle"
        result

        when:
        result = DefaultR2dbcReactorTransactionOperations.isOraclePriorityRollback(connection, exception)

        then:
        1 * connection.getMetadata() >> metadata
        1 * metadata.getDatabaseProductName() >> "PostgreSQL"
        !result
    }

    void "priority rollback detection tolerates a cyclic exception cause chain"() {
        given:
        def connection = Mock(Connection)
        def first = new RuntimeException("first")
        def second = new RuntimeException("second", first)
        first.initCause(second)

        when:
        def result = DefaultR2dbcReactorTransactionOperations.isOraclePriorityRollback(connection, first)

        then:
        !result
        0 * connection.getMetadata()
    }

    private static List oracleListeners() {
        [new OracleTransactionPriorityReactiveTransactionExecutionListener()]
    }

    private static DefaultTransactionDefinition priority(OracleTransactional.Priority priority) {
        def definition = new DefaultTransactionDefinition()
        definition.putProperty(OracleTransactional.ORACLE_PRIORITY, priority)
        return definition
    }

    /**
     * An Oracle connection whose call that sets the priority returns the given previous priority.
     */
    private void oracleConnection(Connection connection, Optional<String> previous) {
        def metadata = Mock(ConnectionMetadata)
        def applyStatement = Mock(Statement)
        def applyResult = Mock(Result)
        connection.getMetadata() >> metadata
        metadata.getDatabaseProductName() >> "Oracle"
        connection.createStatement(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> applyStatement
        applyStatement.bind(0, "LOW") >> applyStatement
        applyStatement.bind(1, _) >> applyStatement
        applyStatement.execute() >> Flux.just(applyResult)
        applyResult.map(_ as java.util.function.Function) >> Flux.just(previous)
    }
}
