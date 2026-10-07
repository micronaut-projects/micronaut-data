package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.ConnectionSynchronization
import io.micronaut.data.connection.exceptions.ConnectionException
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import io.micronaut.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

class DataSourceTransactionManagerBeginFailureSpec extends Specification {

    Connection connection = Mock(Connection)

    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(
        Mock(DataSource),
        Mock(ConnectionOperations),
        Mock(SynchronousConnectionManager)
    )

    DefaultTransactionStatus<Connection> status = DefaultTransactionStatus.newTx(
        Stub(ConnectionStatus) { getConnection() >> connection },
        TransactionDefinition.DEFAULT,
        transactionManager
    )

    def "transaction not started before the begin failure is not rolled back"() {
        given:
            connection.getAutoCommit() >> true
        when:
            transactionManager.doRollbackAfterBeginFailure(status)
        then:
            0 * connection.rollback()
    }

    def "partially started transaction is rolled back after the begin failure"() {
        given:
            connection.getAutoCommit() >> false
        when:
            transactionManager.doRollbackAfterBeginFailure(status)
        then:
            1 * connection.rollback()
    }

    def "failure reading the auto-commit state is reported"() {
        given:
            def failure = new SQLException("closed")
            connection.getAutoCommit() >> { throw failure }
        when:
            transactionManager.doRollbackAfterBeginFailure(status)
        then:
            def e = thrown(TransactionSystemException)
            e.cause.is(failure)
            0 * connection.rollback()
    }

    def "connection changes applied before a failing begin step are restored"() {
        given:
            List<ConnectionSynchronization> synchronizations = []
            def definition = new DefaultTransactionDefinition()
            definition.setReadOnly(true)
            definition.setIsolationLevel(TransactionDefinition.Isolation.REPEATABLE_READ)
            def failingStatus = DefaultTransactionStatus.newTx(
                Stub(ConnectionStatus) {
                    getConnection() >> connection
                    registerSynchronization(_) >> { ConnectionSynchronization s -> synchronizations << s }
                },
                definition,
                transactionManager
            )
            connection.isReadOnly() >> false
            connection.getTransactionIsolation() >> Connection.TRANSACTION_READ_COMMITTED
        when:
            transactionManager.doBegin(failingStatus)
        then:
            1 * connection.setReadOnly(true)
            1 * connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ) >> { throw new SQLException("unsupported isolation") }
            thrown(ConnectionException)
            synchronizations.size() == 1
        when:
            synchronizations*.executionComplete()
        then:
            1 * connection.setReadOnly(false)
            0 * connection.setTransactionIsolation(_)
            0 * connection.setAutoCommit(_)
    }
}
