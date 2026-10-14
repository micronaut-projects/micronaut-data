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
import java.sql.SQLFeatureNotSupportedException
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
        1 * oracle.pingDatabase() >> OracleConnection.DATABASE_OK
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
        2 * oracle.pingDatabase() >> OracleConnection.DATABASE_OK
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLFeatureNotSupportedException("immediate failed") }
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
        1 * oracle.pingDatabase() >> OracleConnection.DATABASE_OK
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

    def "start confirmation failure does not publish a transaction id"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.SUSPEND))
        def state = new OracleSessionlessTransactionState()
        def failure = new SQLException("deferred start failed")

        when:
        PropagatedContext.empty().plus(state).propagate({ handler.begin(status, status.transactionDefinition) })

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> ([1, 2, 3] as byte[])
        1 * oracle.pingDatabase() >> {
            if (throwsException) {
                throw failure
            }
            OracleConnection.DATABASE_CLOSED
        }
        0 * oracle.getTransactionId()
        def e = thrown(CannotCreateTransactionException)
        e.message == "Could not start Oracle sessionless transaction"
        e.cause instanceof SQLException
        e.cause.message == (throwsException ? failure.message : "Could not confirm Oracle sessionless transaction start")
        state.gtrid.isEmpty()

        where:
        throwsException << [false, true]
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
        1 * oracle.pingDatabase() >> OracleConnection.DATABASE_OK
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLFeatureNotSupportedException("immediate failed") }
        1 * oracle.suspendTransaction() >> { throw new SQLException("fallback failed") }
        thrown(TransactionSystemException)
    }

    def "deferred resume errors at suspension are propagated without retry"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.RESUME_AND_SUSPEND))
        def state = new OracleSessionlessTransactionState()
        state.setGtrid([4, 5, 6] as byte[])

        when:
        def completion = PropagatedContext.empty().plus(state).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )
        completion.commit()

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.resumeTransaction(_)
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLException("transaction expired") }
        0 * oracle.suspendTransaction()
        1 * oracle.pingDatabase() >> OracleConnection.DATABASE_OK
        def e = thrown(TransactionSystemException)
        e.cause.message == "transaction expired"
    }

    def "failed suspension round trip is not reported as a successful stage"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.RESUME_AND_SUSPEND))
        def state = new OracleSessionlessTransactionState()
        state.setGtrid([4, 5, 6] as byte[])

        when:
        def completion = PropagatedContext.empty().plus(state).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )
        completion.commit()

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.resumeTransaction(_)
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLFeatureNotSupportedException("immediate failed") }
        1 * oracle.suspendTransaction()
        2 * oracle.pingDatabase() >>> [OracleConnection.DATABASE_OK, OracleConnection.DATABASE_CLOSED]
        def e = thrown(TransactionSystemException)
        e.cause.message == "Could not confirm Oracle sessionless transaction suspension"
        e.cause.suppressed[0].message == "immediate failed"
    }

    def "resumed stage retains its id only after successful suspension"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.RESUME_AND_SUSPEND))
        def state = new OracleSessionlessTransactionState()
        def gtrid = [4, 5, 6] as byte[]
        state.setGtrid(gtrid)

        when:
        def completion = PropagatedContext.empty().plus(state).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )
        if (outcome == TransactionSynchronization.Status.COMMITTED) {
            completion.commit()
        }
        status.triggerAfterCompletion(outcome)

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.resumeTransaction({ Arrays.equals(it, gtrid) })
        (outcome == TransactionSynchronization.Status.COMMITTED ? 1 : 0) * oracle.suspendTransactionImmediately()
        0 * oracle.startTransaction()
        0 * connection.commit()
        state.gtrid.isPresent() == retained
        !retained || Arrays.equals(gtrid, state.gtrid.orElseThrow())

        where:
        outcome                                         | retained
        TransactionSynchronization.Status.COMMITTED    | true
        TransactionSynchronization.Status.ROLLED_BACK  | false
        TransactionSynchronization.Status.UNKNOWN      | false
    }

    def "intermediate stage requires an existing transaction id"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def status = txStatus(Mock(Connection), definition(OracleTransactional.Sessionless.RESUME_AND_SUSPEND))

        when:
        PropagatedContext.empty().plus(new OracleSessionlessTransactionState()).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )

        then:
        thrown(CannotCreateTransactionException)
    }

    def "failed resume does not consume the propagated transaction id"() {
        given:
        def handler = new OracleSessionlessTransactionHandler("test")
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def status = txStatus(connection, definition(OracleTransactional.Sessionless.RESUME_AND_SUSPEND))
        def state = new OracleSessionlessTransactionState()
        def gtrid = [4, 5, 6] as byte[]
        state.setGtrid(gtrid)

        when:
        PropagatedContext.empty().plus(state).propagate(
            { handler.begin(status, status.transactionDefinition) } as Supplier
        )

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.resumeTransaction(_) >> { throw new SQLException("resume failed") }
        thrown(TransactionSystemException)
        Arrays.equals(gtrid, state.gtrid.orElseThrow())
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
