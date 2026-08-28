package io.micronaut.transaction.jdbc.oracle

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.exceptions.CannotCreateTransactionException
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.micronaut.transaction.support.TransactionSynchronization
import oracle.jdbc.OracleConnection
import spock.lang.Specification

import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.function.Supplier

class OracleSessionlessTransactionHandlerSpec extends Specification {

    def "starts a suspendable transaction and returns resource completion"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.SUSPEND, Duration.ofSeconds(5)))
        def state = new OracleSessionlessTransactionState()
        def gtrid = [1, 2, 3] as byte[]

        when:
        def completion = PropagatedContext.empty().plus(state).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction(5) >> gtrid
        Arrays.equals(gtrid, state.gtrid.orElseThrow())

        when:
        completion.commit()

        then:
        1 * oracle.suspendTransactionImmediately()
    }

    def "suspend falls back to non-immediate suspension"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.SUSPEND))
        def state = new OracleSessionlessTransactionState()

        when:
        def completion = PropagatedContext.empty().plus(state).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )
        completion.commit()

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> ([1, 2, 3] as byte[])
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLException("immediate failed") }
        1 * oracle.suspendTransaction()
    }

    def "resumed transaction completion clears the current transaction id"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.REQUIRES_SUSPENDED))
        def state = new OracleSessionlessTransactionState()
        def resumedGtrid = [4, 5, 6] as byte[]
        def replacementGtrid = [7, 8, 9] as byte[]
        state.setGtrid(resumedGtrid)

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, status.transactionDefinition) })
        state.setGtrid(replacementGtrid)
        status.triggerAfterCompletion(TransactionSynchronization.Status.COMMITTED)

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.resumeTransaction({ Arrays.equals(it, resumedGtrid) })
        state.gtrid.isEmpty()
    }

    def "rollback clears the id created by a suspendable transaction"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.SUSPEND))
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, status.transactionDefinition) })
        status.triggerAfterCompletion(TransactionSynchronization.Status.ROLLED_BACK)

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> ([1, 2, 3] as byte[])
        state.gtrid.isEmpty()
    }

    def "requires active propagation state"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def status = txStatus(Mock(Connection), definition(OracleTransactional.Sessionless.SUSPEND))

        when:
        handler.begin(status, status.transactionDefinition)

        then:
        thrown(CannotCreateTransactionException)
    }

    def "reports the datasource when the connection is not Oracle"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("inventory")
        def connection = Mock(Connection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.SUSPEND))
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, status.transactionDefinition) })

        then:
        1 * connection.unwrap(OracleConnection) >> { throw new SQLException("not oracle") }
        def e = thrown(CannotCreateTransactionException)
        e.message.contains("datasource 'inventory'")
    }

    def "start failure is reported without falling back to the current transaction id"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.SUSPEND))
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, status.transactionDefinition) })

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> { throw new SQLException("start failed") }
        0 * oracle.getTransactionId()
        thrown(CannotCreateTransactionException)
    }

    def "suspension failure is reported as transaction system failure"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.SUSPEND))
        def state = new OracleSessionlessTransactionState()

        when:
        def completion = PropagatedContext.empty().plus(state).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )
        completion.commit()

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> ([1, 2, 3] as byte[])
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLException("immediate failed") }
        1 * oracle.suspendTransaction() >> { throw new SQLException("fallback failed") }
        thrown(TransactionSystemException)
    }

    private static DefaultTransactionStatus<Connection> txStatus(Connection connection,
                                                                 TransactionDefinition definition) {
        def connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.named("test"), true, null)
        DefaultTransactionStatus.newTx(connectionStatus, definition, null)
    }

    private static TransactionDefinition definition(OracleTransactional.Sessionless mode,
                                                    Duration timeout = null) {
        def definition = new DefaultTransactionDefinition()
        definition.setName("test")
        if (timeout != null) {
            definition.setTimeout(timeout)
        }
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, mode)
        definition
    }
}
