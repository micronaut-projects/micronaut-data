package io.micronaut.data.jdbc

import io.micronaut.core.order.Ordered
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.jdbc.operations.DataSourceConnectionOperations
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.tests.AbstractTransactionSpec
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.jdbc.DataSourceTransactionManager
import io.micronaut.transaction.support.TransactionExecutionListener
import io.micronaut.transaction.support.TransactionSynchronization

import javax.sql.DataSource
import java.sql.Connection

abstract class AbstractJdbcTransactionSpec extends AbstractTransactionSpec {

    @Override
    protected TransactionOperations getTransactionOperations() {
        return context.getBean(DataSourceTransactionManager)
    }

    @Override
    protected ConnectionOperations getConnectionOperations() {
        return context.getBean(DefaultDataSourceConnectionOperations)
    }

    @Override
    protected Runnable getNoTxCheck() {
        DefaultDataSourceConnectionOperations connectionOperations = context.getBean(DefaultDataSourceConnectionOperations)
        return new Runnable() {
            @Override
            void run() {
                def status = connectionOperations.findConnectionStatus()
                if (status.isEmpty()) {
                    return
                }
                Connection connection = status.get().getConnection()
                // No transaction -> autoCommit == true
                assert connection.getAutoCommit()
            }
        }
    }

    boolean appliesReadOnlyFlagToConnection() {
        return supportsReadOnlyFlag()
    }

    void "read-only transaction on an existing connection restores the connection state"() {
        given:
            def connectionOperations = context.getBean(DefaultDataSourceConnectionOperations)
            def transactionManager = context.getBean(DataSourceTransactionManager)
            def bookRepository = context.getBean(getBookRepositoryClass())
        when:
            def state = connectionOperations.executeWrite { status ->
                Connection connection = status.connection
                def inTx = transactionManager.executeRead { txStatus ->
                    bookRepository.count()
                    [txStatus.connection.autoCommit, txStatus.connection.readOnly]
                }
                [inTx, connection.autoCommit, connection.readOnly]
            }
        then:
            state[0][0] == false
            state[0][1] == appliesReadOnlyFlagToConnection()
            state[1] == true
            state[2] == false
    }

    void "transaction on an existing connection restores the connection state when an after completion synchronization fails"() {
        given:
            def connectionOperations = context.getBean(DefaultDataSourceConnectionOperations)
            def transactionManager = context.getBean(DataSourceTransactionManager)
            def bookRepository = context.getBean(getBookRepositoryClass())
            Connection connection = null
        when:
            connectionOperations.executeWrite { status ->
                connection = status.connection
                try {
                    transactionManager.executeRead { txStatus ->
                        bookRepository.count()
                        txStatus.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            int getOrder() {
                                return Ordered.HIGHEST_PRECEDENCE
                            }

                            @Override
                            void afterCompletion(TransactionSynchronization.Status s) {
                                throw new IllegalStateException("After completion failure")
                            }
                        })
                        return null
                    }
                } finally {
                    assert connection.autoCommit
                    assert !connection.readOnly
                }
            }
        then:
            def e = thrown(IllegalStateException)
            e.message == "After completion failure"
    }

    void "transaction on an existing connection restores the connection state when the begin fails"() {
        given:
            def connectionOperations = context.getBean(DefaultDataSourceConnectionOperations)
            def bookRepository = context.getBean(getBookRepositoryClass())
            def listener = new TransactionExecutionListener<Connection>() {
                @Override
                void afterBegin(ConnectionStatus<Connection> connectionStatus, TransactionDefinition definition) {
                    // A partially started transaction that must not be committed by the auto-commit restore
                    if (!definition.isReadOnly().orElse(false)) {
                        connectionStatus.connection.createStatement().withCloseable {
                            it.executeUpdate("UPDATE book SET title = 'Uncommitted'")
                        }
                    }
                    throw new IllegalStateException("After begin failure")
                }
            }
            def transactionManager = new DataSourceTransactionManager(
                context.getBean(DataSource), connectionOperations, connectionOperations, [listener])
            bookRepository.save(new Book(title: "Committed", totalPages: 10))
            Connection connection = null
        when:
            connectionOperations.executeWrite { status ->
                connection = status.connection
                try {
                    transactionManager.executeWrite { txStatus -> null }
                } finally {
                    assert connection.autoCommit
                    assert !connection.readOnly
                }
            }
        then:
            def e = thrown(IllegalStateException)
            e.message == "After begin failure"
            bookRepository.findAll()*.title == ["Committed"]
        when:
            connectionOperations.executeWrite { status ->
                connection = status.connection
                try {
                    transactionManager.executeRead { txStatus -> null }
                } finally {
                    assert connection.autoCommit
                    assert !connection.readOnly
                }
            }
        then:
            e = thrown(IllegalStateException)
            e.message == "After begin failure"
            bookRepository.findAll()*.title == ["Committed"]
    }

}
