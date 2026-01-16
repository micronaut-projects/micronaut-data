package io.micronaut.transaction.jdbc

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.jdbc.mock.MockConnection
import io.micronaut.transaction.jdbc.mock.MockDataSource
import io.micronaut.transaction.jdbc.mock.TestSyncTracker
import io.micronaut.transaction.jdbc.mock.SyncTrackerSynchronization
import io.micronaut.transaction.jdbc.mock.ThrowingExecutionCompleteSynchronization
import jakarta.inject.Inject

import java.sql.Connection
import java.sql.SQLException

@MicronautTest(transactional = false, environments = "broken-conn")
class BrokenConnectionPropagationSpec extends spock.lang.Specification {

    @Inject
    SynchronousTransactionManager<Connection> txManager

    @Inject
    MockDataSource mockDataSource

    @Inject
    TestSyncTracker tracker

    def "reproducer: closed connection remains cached in PropagatedContext after rollback/reset throws"() {
        given: "a new transaction is opened using the synchronous transaction API (context-bound connection)"
        tracker.reset()
        def status = txManager.getTransaction(TransactionDefinition.DEFAULT)
        def firstConn = status.getConnection() as MockConnection
        int firstId = firstConn.id()

        and: "register connection synchronizations including one that throws during executionComplete"
        status.getConnectionStatus().registerSynchronization(new SyncTrackerSynchronization(tracker, 0))
        status.getConnectionStatus().registerSynchronization(new ThrowingExecutionCompleteSynchronization(1))

        when: "the underlying connection becomes broken/closed before rollback (e.g. socket timeout)"
        mockDataSource.getLastConnection().breakAndClose()
        txManager.rollback(status)

        then: "rollback fails (simulated driver throws due to closed connection or sync failure)"
        def ex = thrown(Throwable)
        assert ex instanceof io.micronaut.data.connection.exceptions.ConnectionException || ex instanceof IllegalStateException
        and: "all connection synchronizations executed even when one throws"
        assert tracker.executionComplete.get() >= 1
        assert tracker.beforeClosed.get() >= 1
        assert tracker.afterClosed.get() >= 1

        when: "on the same thread, a subsequent transaction is started"
        def status2 = txManager.getTransaction(TransactionDefinition.DEFAULT)
        def newConn = status2.getConnection() as MockConnection
        int secondId = newConn.id()
        // any simple operation should now succeed on a fresh, open connection
        newConn.getAutoCommit()

        then: "a fresh connection is obtained and is not the previously closed one"
        secondId != firstId
        and: "a new connection was created"
        mockDataSource.getCreatedCount() == 2

        when: "the new transaction is committed to complete the cleanup path"
        txManager.commit(status2)

        then:
        noExceptionThrown()
    }
}
