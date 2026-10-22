package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.tck.entities.Book
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
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
}
