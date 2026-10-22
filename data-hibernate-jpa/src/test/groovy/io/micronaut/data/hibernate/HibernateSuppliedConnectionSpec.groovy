package io.micronaut.data.hibernate

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.tck.entities.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.exceptions.TransactionUsageException
import jakarta.inject.Inject
import org.hibernate.Session
import org.hibernate.engine.spi.SessionImplementor
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.ResultSet

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
@H2DBProperties
class HibernateSuppliedConnectionSpec extends Specification {

    @Inject
    ConnectionOperations<Connection> connectionOperations

    @Inject
    TransactionOperations<Session> transactionOperations

    @Inject
    DataSource dataSource

    void "Hibernate work inside the callback runs on a session over the supplied JDBC connection"() {
        given:
        DataSource rawDataSource = DelegatingDataSource.unwrapDataSource(dataSource)
        Connection supplied = rawDataSource.connection
        long before = countBooks(supplied)

        when:
        connectionOperations.executeWithConnection(supplied, ConnectionDefinition.DEFAULT, status -> {
            assert status.connection.is(supplied)
            transactionOperations.executeWrite(tx -> {
                assert physicalConnection(tx.connection).is(supplied)
                tx.connection.persist(new Book(title: "Supplied to Hibernate", totalPages: 10))
                tx.connection.flush()
                // only the supplied connection sees the uncommitted row
                Connection other = rawDataSource.connection
                try {
                    assert countBooks(supplied) == before + 1
                    assert countBooks(other) == before
                } finally {
                    other.close()
                }
            })
        })

        then: "the transaction committed the supplied connection, which stays open"
        countBooks(supplied) == before + 1
        !supplied.closed

        cleanup:
        supplied?.close()
    }

    void "with auto-commit off, a Hibernate transaction in the callback fails instead of taking over the owner's transaction"() {
        given:
        DataSource rawDataSource = DelegatingDataSource.unwrapDataSource(dataSource)
        Connection owner = rawDataSource.connection
        owner.autoCommit = false
        long before = countBooks(owner)

        when:
        connectionOperations.executeWithConnection(owner, ConnectionDefinition.DEFAULT, status ->
            transactionOperations.executeWrite(tx -> {
                tx.connection.persist(new Book(title: "Never written", totalPages: 10))
            })
        )

        then: "nothing was written, and the owner's transaction is left as it was"
        TransactionUsageException e = thrown()
        e.message.contains("cannot join the transaction of a supplied connection")
        countBooks(owner) == before
        !owner.autoCommit
        countCommitted(rawDataSource) == before

        cleanup:
        owner?.autoCommit = true
        owner?.close()
    }

    private static long countCommitted(DataSource dataSource) {
        Connection other = dataSource.connection
        try {
            return countBooks(other)
        } finally {
            other.close()
        }
    }

    private static Connection physicalConnection(Session session) {
        return ((SessionImplementor) session).jdbcCoordinator.logicalConnection.physicalConnection
    }

    private static long countBooks(Connection connection) {
        ResultSet resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) FROM book")
        try {
            resultSet.next()
            return resultSet.getLong(1)
        } finally {
            resultSet.close()
        }
    }
}
