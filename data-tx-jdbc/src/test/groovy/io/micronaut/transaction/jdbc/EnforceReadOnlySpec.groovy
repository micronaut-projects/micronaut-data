package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.exceptions.CannotCreateTransactionException
import io.micronaut.transaction.sessionless.SessionlessTransactionHandler
import io.micronaut.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.function.Function

class EnforceReadOnlySpec extends Specification {

    Connection connection = Mock(Connection)
    ConnectionOperations<Connection> connectionOperations = Mock(ConnectionOperations)
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(Mock(DataSource), connectionOperations, null)

    def setup() {
        connection.getAutoCommit() >> true
        connectionOperations.execute(_, _) >> { ConnectionDefinition definition, Function callback ->
            callback.apply(new DefaultConnectionStatus<>(connection, definition, true, connectionOperations))
        }
    }

    void "read-only transaction is enforced with a statement"() {
        given:
        def statement = Mock(Statement)
        transactionManager.setEnforceReadOnly(true)

        when:
        transactionManager.execute(readOnly(true)) { status -> null }

        then:
        1 * connection.setAutoCommit(false)

        then:
        1 * connection.createStatement() >> statement
        1 * statement.executeUpdate("SET TRANSACTION READ ONLY")
        1 * statement.close()

        then:
        1 * connection.commit()
    }

    void "read-only transaction is not enforced by default"() {
        when:
        transactionManager.execute(readOnly(true)) { status -> null }

        then:
        !transactionManager.isEnforceReadOnly()
        0 * connection.createStatement()
        1 * connection.commit()
    }

    void "read-write transaction is not enforced"() {
        given:
        transactionManager.setEnforceReadOnly(true)

        when:
        transactionManager.execute(readOnly(false)) { status -> null }

        then:
        0 * connection.createStatement()
        1 * connection.commit()
    }

    void "failure to enforce a read-only transaction fails the begin"() {
        given:
        transactionManager.setEnforceReadOnly(true)

        when:
        transactionManager.execute(readOnly(true)) { status -> null }

        then:
        1 * connection.createStatement() >> { throw new SQLException("not supported") }
        0 * connection.commit()
        def e = thrown(CannotCreateTransactionException)
        e.cause instanceof SQLException
    }

    void "a new connection is completed when the transaction fails to begin"() {
        given:
        def connectionManager = Mock(SynchronousConnectionManager)
        def connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.DEFAULT, true, connectionOperations)
        def manager = new DataSourceTransactionManager(Mock(DataSource), connectionOperations, connectionManager)
        manager.setEnforceReadOnly(true)
        connectionOperations.findConnectionStatus() >> Optional.empty()

        when:
        manager.getTransaction(readOnly(true))

        then:
        1 * connectionManager.getConnection(_) >> connectionStatus
        1 * connection.createStatement() >> { throw new SQLException("not supported") }
        1 * connectionManager.complete(connectionStatus)
        thrown(CannotCreateTransactionException)
    }

    void "read-only sessionless transaction is not enforced with a statement"() {
        given:
        def sessionlessTransactionHandler = Mock(SessionlessTransactionHandler)
        def manager = new DataSourceTransactionManager(Mock(DataSource), connectionOperations, null, List.of(), sessionlessTransactionHandler)
        manager.setEnforceReadOnly(true)
        def definition = readOnly(true)
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, OracleTransactional.Sessionless.SUSPEND)

        when:
        manager.execute(definition) { status -> null }

        then:
        _ * sessionlessTransactionHandler.supports(_) >> true
        1 * connection.setReadOnly(true)
        0 * connection.createStatement()
        1 * sessionlessTransactionHandler.begin(_, definition) >> null
        1 * connection.commit()
    }

    private static DefaultTransactionDefinition readOnly(boolean readOnly) {
        def definition = new DefaultTransactionDefinition()
        definition.setReadOnly(readOnly)
        definition
    }
}
