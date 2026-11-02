package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.connection.exceptions.ConnectionException
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.exceptions.TransactionUsageException
import io.micronaut.transaction.exceptions.UnexpectedRollbackException
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.micronaut.transaction.support.TransactionSynchronization
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection

class H2SuppliedConnectionSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    H2BookRepository bookRepository = context.getBean(H2BookRepository)

    @Shared
    ConnectionOperations<Connection> connectionOperations = context.getBean(ConnectionOperations)

    @Shared
    TransactionOperations<Connection> transactionOperations = context.getBean(TransactionOperations)

    @Shared
    DataSource rawDataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))

    def cleanup() {
        bookRepository.deleteAll()
    }

    void "repositories use the supplied connection, and the caller commits it"() {
        given:
        Connection supplied = rawDataSource.connection
        supplied.autoCommit = false

        when:
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, status -> {
            assert status.connection.is(supplied)
            bookRepository.save(new Book(title: "Supplied", totalPages: 10))
        })

        then: 'the insert is not visible before the owner commits'
        bookRepository.count() == 0
        !supplied.closed

        when:
        supplied.commit()

        then:
        bookRepository.count() == 1

        cleanup:
        supplied.autoCommit = true
        supplied.close()
    }

    void "a transaction inside the callback commits the supplied connection"() {
        given:
        Connection supplied = rawDataSource.connection

        when:
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, status ->
            transactionOperations.executeWrite(tx -> {
                assert tx.connection.is(supplied)
                bookRepository.save(new Book(title: "Transactional", totalPages: 20))
            })
        )

        then:
        bookRepository.count() == 1
        !supplied.closed

        cleanup:
        supplied.close()
    }

    void "a transaction inside the callback rolls back the supplied connection"() {
        given:
        Connection supplied = rawDataSource.connection

        when:
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, status ->
            transactionOperations.executeWrite(tx -> {
                assert tx.connection.is(supplied)
                assert !supplied.autoCommit
                bookRepository.save(new Book(title: "Rolled back", totalPages: 30))
                throw new IllegalStateException("boom")
            })
        )

        then:
        thrown(IllegalStateException)
        bookRepository.count() == 0
        !supplied.closed

        cleanup:
        supplied.close()
    }

    void "inside a caller's transaction, a transaction in the callback begins on the supplied connection"() {
        given:
        Connection supplied = rawDataSource.connection

        when: "the caller's transaction rolls back after the callback"
        transactionOperations.executeWrite(outer -> {
            connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, status ->
                transactionOperations.executeWrite(inner -> {
                    assert inner.connection.is(supplied)
                    assert inner.newTransaction
                    bookRepository.save(new Book(title: "Inner", totalPages: 40))
                })
            )
            assert transactionOperations.findTransactionStatus().get().is(outer)
            bookRepository.save(new Book(title: "Outer", totalPages: 50))
            outer.setRollbackOnly()
        })

        then: "the inner transaction committed the supplied connection, and the caller's work rolled back"
        bookRepository.findAll()*.title == ["Inner"]
        !supplied.closed

        cleanup:
        supplied.close()
    }

    void "when the owner controls the transaction, a transaction in the callback joins it"() {
        given: "the owner has started a transaction and saved a book"
        List<String> calls = []
        Connection owner = rawDataSource.connection
        owner.autoCommit = false
        Connection supplied = recording(owner, calls)
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, true, status ->
            bookRepository.save(new Book(title: "Owner", totalPages: 1)))

        when: "a transaction inside the callback saves another and completes"
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, true, status ->
            transactionOperations.execute(isolated(), tx -> {
                assert !tx.newTransaction
                bookRepository.save(new Book(title: "Joined", totalPages: 2))
            })
        )

        then: "it neither changed the connection's mode nor committed it"
        calls.findAll { it in ['setAutoCommit', 'setTransactionIsolation', 'commit', 'rollback'] }.isEmpty()
        bookRepository.count() == 0

        when: "the owner rolls back"
        owner.rollback()

        then:
        bookRepository.count() == 0

        cleanup:
        owner.autoCommit = true
        owner.close()
    }

    void "when the owner controls the transaction, a failed transaction in the callback leaves the rollback to the owner"() {
        given:
        List<String> calls = []
        Connection owner = rawDataSource.connection
        owner.autoCommit = false
        Connection supplied = recording(owner, calls)
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, true, status ->
            bookRepository.save(new Book(title: "Owner", totalPages: 1)))

        when: "the failure propagates"
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, true, status ->
            transactionOperations.executeWrite(tx -> {
                bookRepository.save(new Book(title: "Failed", totalPages: 2))
                throw new IllegalStateException("boom")
            })
        )

        then: "the exception reaches the owner, and the owner's earlier work is untouched"
        thrown(IllegalStateException)
        calls.findAll { it in ['setAutoCommit', 'commit', 'rollback'] }.isEmpty()

        when: "the failure is handled inside the callback"
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, true, status -> {
            try {
                transactionOperations.executeWrite(tx -> { throw new IllegalStateException("handled") })
            } catch (IllegalStateException ignored) {
            }
        })

        then: "the owner is still told to roll back"
        ConnectionException e = thrown()
        e.message.contains("must roll back")

        when: "the owner commits what remains, its own book and the failed transaction's insert"
        owner.commit()

        then: "rolling back was the owner's decision"
        bookRepository.findAll()*.title.sort() == ["Failed", "Owner"]

        cleanup:
        owner.autoCommit = true
        owner.close()
    }

    void "when the owner controls the transaction, REQUIRES_NEW in the callback runs on a separate connection"() {
        given:
        Connection owner = rawDataSource.connection
        owner.autoCommit = false

        when:
        connectionOperations.executeWithConnection(owner, ConnectionDefinition.DEFAULT, true, status ->
            transactionOperations.execute(TransactionDefinition.of(TransactionDefinition.Propagation.REQUIRES_NEW), tx -> {
                assert !tx.connection.is(owner)
                bookRepository.save(new Book(title: "Independent", totalPages: 3))
            })
        )
        owner.rollback()

        then:
        bookRepository.findAll()*.title == ["Independent"]

        cleanup:
        owner.autoCommit = true
        owner.close()
    }

    void "a joined transaction cannot register synchronizations, since the owner's commit is not seen"() {
        given:
        Connection owner = rawDataSource.connection
        owner.autoCommit = false

        when:
        connectionOperations.executeWithConnection(owner, ConnectionDefinition.DEFAULT, true, status ->
            transactionOperations.executeWrite(tx -> {
                tx.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    void afterCommit() {
                    }
                })
            })
        )

        then:
        TransactionUsageException e = thrown()
        e.message.contains("cannot be registered on a transaction joined to the owner's transaction")

        cleanup:
        owner.rollback()
        owner.autoCommit = true
        owner.close()
    }

    void "after a handled rollback, later joined transactions fail before running their work"() {
        given:
        Connection owner = rawDataSource.connection
        owner.autoCommit = false
        boolean secondRan = false

        when:
        connectionOperations.executeWithConnection(owner, ConnectionDefinition.DEFAULT, true, status -> {
            try {
                transactionOperations.executeWrite(tx -> { throw new IllegalStateException("handled") })
            } catch (IllegalStateException ignored) {
            }
            transactionOperations.executeWrite(tx -> {
                secondRan = true
                bookRepository.save(new Book(title: "After rollback", totalPages: 1))
            })
        })

        then:
        thrown(UnexpectedRollbackException)
        !secondRan

        cleanup:
        owner.rollback()
        owner.autoCommit = true
        owner.close()
    }

    void "without the owner flag, Micronaut Data controls the transaction even when auto-commit is off"() {
        given:
        Connection supplied = rawDataSource.connection
        supplied.autoCommit = false

        when:
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, status ->
            transactionOperations.executeWrite(tx -> {
                assert tx.newTransaction
                bookRepository.save(new Book(title: "Committed", totalPages: 1))
            })
        )

        then: "the transaction committed the supplied connection"
        bookRepository.findAll()*.title == ["Committed"]

        cleanup:
        supplied.autoCommit = true
        supplied.close()
    }

    private static TransactionDefinition isolated() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition()
        definition.isolationLevel = TransactionDefinition.Isolation.SERIALIZABLE
        return definition
    }

    private static Connection recording(Connection connection, List<String> calls) {
        return (Connection) Proxy.newProxyInstance(Connection.classLoader, [Connection] as Class[], { proxy, Method method, Object[] args ->
            calls << method.name
            try {
                return method.invoke(connection, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
        } as InvocationHandler)
    }
}
