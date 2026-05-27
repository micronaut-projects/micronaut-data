package io.micronaut.transaction.jdbc.oracle

import io.micronaut.context.ApplicationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.CannotCreateTransactionException
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import io.micronaut.transaction.support.TransactionExecutionListener
import oracle.jdbc.OracleConnection
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration

class OracleSessionlessTransactionManagerSpec extends Specification {

    def "oracle manager is disabled when datasource selects another transaction manager"() {
        given:
        def context = ApplicationContext.run([
            "datasources.default.url"                : "jdbc:h2:mem:oracleSessionlessTxCondition;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            "datasources.default.transaction-manager": "hibernate"
        ])

        expect:
        !context.containsBean(OracleSessionlessTransactionManager)

        cleanup:
        context.close()
    }

    def "begin delegates JDBC setup and starts sessionless transaction with listener support"() {
        given:
        def listener = Mock(TransactionExecutionListener)
        def manager = newTransactionManager([listener])
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND, Duration.ofSeconds(5))
        def connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.named("test"), true, null)
        def status = DefaultTransactionStatus.newTx(connectionStatus, definition, manager)
        def gtrid = [1, 2, 3] as byte[]
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ manager.doBegin(status) })

        then:
        1 * listener.beforeBegin(connectionStatus, definition)
        1 * connection.getAutoCommit() >> true
        1 * connection.setAutoCommit(false)
        1 * listener.afterBegin(connectionStatus, definition)
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction(5) >> gtrid
        0 * oracle.getTransactionId()
        Arrays.equals(gtrid, state.gtrid.orElseThrow())
    }

    def "start transaction failure does not fall back to current transaction id"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND)
        def status = txStatus(connection, definition, manager)
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ manager.doBegin(status) })

        then:
        1 * connection.getAutoCommit() >> false
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.startTransaction() >> { throw new SQLException("start failed") }
        0 * oracle.getTransactionId()
        thrown(CannotCreateTransactionException)
    }

    def "sessionless begin requires an Oracle connection"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND)
        def status = txStatus(connection, definition, manager)
        def state = new OracleSessionlessTransactionState()

        when:
        PropagatedContext.empty().plus(state).propagate({ manager.doBegin(status) })

        then:
        1 * connection.getAutoCommit() >> false
        1 * connection.unwrap(OracleConnection) >> { throw new SQLException("not oracle") }
        thrown(CannotCreateTransactionException)
    }

    def "sessionless begin requires active propagation state"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND)
        def status = txStatus(connection, definition, manager)

        when:
        manager.doBegin(status)

        then:
        thrown(CannotCreateTransactionException)
        0 * connection._
    }

    def "sessionless begin rejects a second suspended transaction id"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND)
        def status = txStatus(connection, definition, manager)
        def state = new OracleSessionlessTransactionState()
        state.setGtrid([9, 9, 9] as byte[])

        when:
        PropagatedContext.empty().plus(state).propagate({ manager.doBegin(status) })

        then:
        thrown(CannotCreateTransactionException)
        0 * connection._
    }

    def "suspend commit falls back to non-immediate suspend"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def oracle = Mock(OracleConnection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND)
        def status = txStatus(connection, definition, manager)

        when:
        manager.doCommit(status)

        then:
        1 * connection.unwrap(OracleConnection) >> oracle
        1 * oracle.suspendTransactionImmediately() >> { throw new SQLException("immediate failed") }
        1 * oracle.suspendTransaction()
        0 * connection.commit()
    }

    def "suspended transaction context is cleared on rollback"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND)
        def status = txStatus(connection, definition, manager)
        def state = new OracleSessionlessTransactionState()
        state.setGtrid([1, 2, 3] as byte[])

        when:
        Boolean presentAfterRollback = null
        PropagatedContext.empty().plus(state).propagate({
            manager.doRollback(status)
            presentAfterRollback = state.gtrid.isPresent()
        })

        then:
        1 * connection.rollback()
        !presentAfterRollback
        state.gtrid.isEmpty()
    }

    def "suspended transaction context is cleared when rollback fails"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def definition = definition(TransactionDefinition.Propagation.SUSPEND)
        def status = txStatus(connection, definition, manager)
        def state = new OracleSessionlessTransactionState()
        state.setGtrid([1, 2, 3] as byte[])

        when:
        TransactionSystemException thrownException = null
        Boolean presentAfterRollback = null
        PropagatedContext.empty().plus(state).propagate({
            try {
                manager.doRollback(status)
            } catch (TransactionSystemException e) {
                thrownException = e
                presentAfterRollback = state.gtrid.isPresent()
            }
        })

        then:
        1 * connection.rollback() >> { throw new SQLException("rollback failed") }
        thrownException != null
        !presentAfterRollback
        state.gtrid.isEmpty()
    }

    def "resumed transaction context is cleared when commit fails"() {
        given:
        def manager = newTransactionManager()
        def connection = Mock(Connection)
        def definition = definition(TransactionDefinition.Propagation.REQUIRES_SUSPENDED)
        def status = txStatus(connection, definition, manager)
        def state = new OracleSessionlessTransactionState()
        state.setGtrid([4, 5, 6] as byte[])

        when:
        TransactionSystemException thrownException = null
        Boolean presentAfterCommit = null
        PropagatedContext.empty().plus(state).propagate({
            try {
                manager.doCommit(status)
            } catch (TransactionSystemException e) {
                thrownException = e
                presentAfterCommit = state.gtrid.isPresent()
            }
        })

        then:
        1 * connection.commit() >> { throw new SQLException("commit failed") }
        thrownException != null
        !presentAfterCommit
    }

    private OracleSessionlessTransactionManager newTransactionManager(List<TransactionExecutionListener<Connection>> listeners = Collections.emptyList()) {
        new OracleSessionlessTransactionManager(
            Mock(DataSource),
            Mock(ConnectionOperations),
            Mock(SynchronousConnectionManager),
            listeners
        )
    }

    private static DefaultTransactionStatus<Connection> txStatus(Connection connection,
                                                                 TransactionDefinition definition,
                                                                 OracleSessionlessTransactionManager manager) {
        def connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.named("test"), true, null)
        DefaultTransactionStatus.newTx(connectionStatus, definition, manager)
    }

    private static TransactionDefinition definition(TransactionDefinition.Propagation propagation,
                                                    Duration timeout = null) {
        new TransactionDefinition() {
            @Override
            String getName() {
                "test"
            }

            @Override
            TransactionDefinition.Propagation getPropagationBehavior() {
                propagation
            }

            @Override
            Optional<Duration> getTimeout() {
                Optional.ofNullable(timeout)
            }
        }
    }
}
