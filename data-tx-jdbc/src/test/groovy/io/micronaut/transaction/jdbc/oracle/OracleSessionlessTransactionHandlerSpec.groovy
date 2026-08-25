package io.micronaut.transaction.jdbc.oracle

import io.micronaut.context.ApplicationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.exceptions.CannotCreateTransactionException
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import io.micronaut.transaction.sessionless.SessionlessTransactionHandler
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.micronaut.transaction.support.TransactionSynchronization
import oracle.jdbc.OracleConnection
import spock.lang.Specification

import java.sql.Connection
import java.sql.SQLException
import java.time.Duration

class OracleSessionlessTransactionHandlerSpec extends Specification {

    def "handler is not registered when the datasource selects another transaction manager"() {
        given:
        def context = ApplicationContext.run([
            "datasources.default.url"                : "jdbc:h2:mem:oracleSessionlessHandlerCondition;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            "datasources.default.transaction-manager": "hibernate"
        ])

        expect:
        !context.containsBean(SessionlessTransactionHandler)

        cleanup:
        context.close()
    }

    def "suspend mode starts the transaction and publishes the id only once the suspend succeeded"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def definition = definition(OracleTransactional.Sessionless.SUSPEND, Duration.ofSeconds(5))
        def status = txStatus(connection, definition)
        def state = new OracleSessionlessTransactionState()
        def gtrid = [1, 2, 3] as byte[]

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, definition) })

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction(5) >> gtrid
        0 * oracle.getTransactionId()
        0 * oracle.suspendTransactionImmediately()
        state.gtrid.isEmpty()

        when: "the transaction reaches its commit boundary"
        status.triggerBeforeCommit()

        then:
        1 * oracle.suspendTransactionImmediately()
        Arrays.equals(gtrid, state.gtrid.orElseThrow())

        when:
        status.triggerAfterCompletion(TransactionSynchronization.Status.COMMITTED)

        then:
        Arrays.equals(gtrid, state.gtrid.orElseThrow())
    }

    def "suspend mode leaves no transaction id behind when the transaction rolls back"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def definition = definition(OracleTransactional.Sessionless.SUSPEND)
        def status = txStatus(connection, definition)
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, definition) })
        status.triggerAfterCompletion(TransactionSynchronization.Status.ROLLED_BACK)

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> ([9] as byte[])
        0 * oracle.suspendTransactionImmediately()
        state.gtrid.isEmpty()
    }

    def "a failed suspend does not publish a transaction id"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def definition = definition(OracleTransactional.Sessionless.SUSPEND)
        def status = txStatus(connection, definition)
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, definition) })

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> ([1, 2] as byte[])

        when:
        status.triggerBeforeCommit()

        then:
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLException("suspend failed") }
        thrown(TransactionSystemException)
        state.gtrid.isEmpty()
    }

    def "resume mode resumes the transaction and clears the id on completion"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def definition = definition(OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
        def status = txStatus(connection, definition)
        def state = new OracleSessionlessTransactionState()
        def gtrid = [4, 5, 6] as byte[]
        state.setGtrid(gtrid)

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, definition) })

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.resumeTransaction({ byte[] value -> Arrays.equals(gtrid, value) })

        when:
        status.triggerAfterCompletion(completionStatus)

        then:
        state.gtrid.isEmpty()

        where:
        completionStatus << [
            TransactionSynchronization.Status.COMMITTED,
            TransactionSynchronization.Status.ROLLED_BACK
        ]
    }

    def "begin requires active propagation state"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def definition = definition(OracleTransactional.Sessionless.SUSPEND)
        def status = txStatus(connection, definition)

        when:
        handler.begin(status, definition)

        then:
        thrown(CannotCreateTransactionException)
        0 * connection._
    }

    def "begin rejects a second suspended transaction id"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def definition = definition(OracleTransactional.Sessionless.SUSPEND)
        def status = txStatus(connection, definition)
        def state = new OracleSessionlessTransactionState()
        state.setGtrid([9, 9, 9] as byte[])

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, definition) })

        then:
        thrown(CannotCreateTransactionException)
        0 * connection._
    }

    def "resume without a transaction id fails"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def definition = definition(OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
        def status = txStatus(connection, definition)
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, definition) })

        then:
        thrown(CannotCreateTransactionException)
        0 * connection._
    }

    def "begin requires an Oracle connection"() {
        given:
        def handler = new OracleSessionlessTransactionHandler()
        def connection = Mock(Connection)
        def definition = definition(OracleTransactional.Sessionless.SUSPEND)
        def status = txStatus(connection, definition)
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, definition) })

        then:
        1 * connection.unwrap(OracleConnection) >> { throw new SQLException("not oracle") }
        thrown(CannotCreateTransactionException)
    }

    private static DefaultTransactionStatus<Connection> txStatus(Connection connection, TransactionDefinition definition) {
        def connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.named("test"), true, null)
        DefaultTransactionStatus.newTx(connectionStatus, definition, null)
    }

    private static TransactionDefinition definition(OracleTransactional.Sessionless mode, Duration timeout = null) {
        def definition = new DefaultTransactionDefinition()
        definition.setName("test")
        if (timeout != null) {
            definition.setTimeout(timeout)
        }
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, mode)
        definition
    }
}
