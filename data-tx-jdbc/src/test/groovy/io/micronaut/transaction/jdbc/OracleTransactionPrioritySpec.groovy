package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.DefaultConnectionDefinition
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.exceptions.CannotCreateTransactionException
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException
import java.sql.Statement

/**
 * Verifies that Oracle transaction priority is applied (ALTER SESSION "txn_priority")
 * at transaction begin and reset after completion when @OracleTransactional is present.
 */
class OracleTransactionPrioritySpec extends Specification {

    def "applies and resets Oracle txn_priority when oracle priority is present"() {
        given: "Mocks for Oracle connection and statement"
        def dataSource = Mock(DataSource)
        def connection = Mock(Connection)
        def meta = Mock(DatabaseMetaData)
        def stmt = Mock(Statement)

        and: "Connection definition carrying that annotation metadata"
        ConnectionDefinition connDef = new DefaultConnectionDefinition("test")

        and: "Connection status wrapping the connection and allowing synchronizations"
        def status = new DefaultConnectionStatus<>(connection, connDef, true, null)

        and: "A DefaultTransactionStatus with the above connection status"
        def txDef = createWithPriority(OracleTransactional.Priority.LOW)
        def txManager = new DataSourceTransactionManager(dataSource, Mock(io.micronaut.data.connection.ConnectionOperations), Mock(io.micronaut.data.connection.SynchronousConnectionManager))
        def txStatus = DefaultTransactionStatus.newTx(status, txDef, txManager)

        and: "Oracle connection behavior"
        connection.getMetaData() >> meta
        meta.getDatabaseProductName() >> "Oracle"
        connection.getAutoCommit() >> true
        connection.setAutoCommit(false) >> { }
        connection.isReadOnly() >> false
        connection.getTransactionIsolation() >> Connection.TRANSACTION_READ_COMMITTED
        2 * connection.createStatement() >> stmt
        _ * stmt.close()

        when: "Beginning the transaction"
        txManager.doBegin(txStatus)

        then: "Priority is set to LOW"
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="LOW"')

        when: "Execution completes (triggers onComplete reset)"
        status.complete()

        then: "Priority is reset to HIGH"
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="HIGH"')
    }

    def "ignores ORA-02248 while setting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.MEDIUM)
        def stmt = Mock(Statement)
        1 * fixture.connection.createStatement() >> stmt
        _ * stmt.close()
        stmt.executeUpdate('ALTER SESSION SET "txn_priority"="MEDIUM"') >> { throw new SQLException('ORA-02248: invalid option for ALTER SESSION', '42000', 2248) }

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then:
        noExceptionThrown()
    }

    def "ignores chained ORA-02248 while setting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.MEDIUM)
        def stmt = Mock(Statement)
        1 * fixture.connection.createStatement() >> stmt
        _ * stmt.close()
        def exception = new SQLException('Statement failed', '42000', 0)
        exception.setNextException(new SQLException('ORA-02248: invalid option for ALTER SESSION', '42000', 2248))
        stmt.executeUpdate('ALTER SESSION SET "txn_priority"="MEDIUM"') >> { throw exception }

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then:
        noExceptionThrown()
    }

    def "rethrows non-ORA-02248 failure while setting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.MEDIUM)
        def stmt = Mock(Statement)
        fixture.connection.createStatement() >> stmt
        _ * stmt.close()
        stmt.executeUpdate('ALTER SESSION SET "txn_priority"="MEDIUM"') >> { throw new SQLException('ORA-01031: insufficient privileges', '42000', 1031) }

        when:
        txManager.doBegin(fixture.txStatus)

        then:
        def e = thrown(CannotCreateTransactionException)
        e.cause instanceof SQLException
        e.cause.errorCode == 1031
    }

    def "ignores ORA-02248 while resetting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.LOW)
        def stmt = Mock(Statement)
        2 * fixture.connection.createStatement() >> stmt
        _ * stmt.close()
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="LOW"')
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="HIGH"') >> { throw new SQLException('ORA-02248: invalid option for ALTER SESSION', '42000', 2248) }

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then:
        noExceptionThrown()
    }

    def "rethrows non-ORA-02248 failure while resetting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.LOW)
        def stmt = Mock(Statement)
        2 * fixture.connection.createStatement() >> stmt
        _ * stmt.close()
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="LOW"')
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="HIGH"') >> { throw new SQLException('ORA-01031: insufficient privileges', '42000', 1031) }

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then:
        def e = thrown(TransactionSystemException)
        e.cause instanceof SQLException
        e.cause.errorCode == 1031
    }

    def "no priority applied for non-Oracle databases"() {
        given:
        def dataSource = Mock(DataSource)
        def connection = Mock(Connection)
        def meta = Mock(DatabaseMetaData)
        ConnectionDefinition connDef = new DefaultConnectionDefinition("test")
        def status = new DefaultConnectionStatus<>(connection, connDef, true, null)
        def txDef = createWithPriority(OracleTransactional.Priority.HIGH)
        def txManager = new DataSourceTransactionManager(dataSource, Mock(io.micronaut.data.connection.ConnectionOperations), Mock(io.micronaut.data.connection.SynchronousConnectionManager))
        def txStatus = DefaultTransactionStatus.newTx(status, txDef, txManager)

        and: "Non-Oracle database"
        connection.getMetaData() >> meta
        meta.getDatabaseProductName() >> "H2"
        connection.getAutoCommit() >> true
        connection.setAutoCommit(false) >> { }
        connection.isReadOnly() >> false
        connection.getTransactionIsolation() >> Connection.TRANSACTION_READ_COMMITTED

        when:
        txManager.doBegin(txStatus)
        status.complete()

        then: "No ALTER SESSION is executed"
        0 * connection.createStatement()
    }

    private DataSourceTransactionManager newTxManager() {
        new DataSourceTransactionManager(Mock(DataSource), Mock(io.micronaut.data.connection.ConnectionOperations), Mock(io.micronaut.data.connection.SynchronousConnectionManager))
    }

    private OracleTxFixture newOracleTxFixture(DataSourceTransactionManager txManager, OracleTransactional.Priority priority) {
        def connection = Mock(Connection)
        def meta = Mock(DatabaseMetaData)
        connection.getMetaData() >> meta
        meta.getDatabaseProductName() >> 'Oracle'
        connection.getAutoCommit() >> true
        connection.setAutoCommit(false) >> { }
        connection.isReadOnly() >> false
        connection.getTransactionIsolation() >> Connection.TRANSACTION_READ_COMMITTED

        ConnectionDefinition connDef = new DefaultConnectionDefinition('test')
        def status = new DefaultConnectionStatus<>(connection, connDef, true, null)
        def txDef = createWithPriority(priority)
        def txStatus = DefaultTransactionStatus.newTx(status, txDef, txManager)
        new OracleTxFixture(connection, status, txStatus)
    }

    static TransactionDefinition createWithPriority(OracleTransactional.Priority priority) {
        return new TransactionDefinition() {
            @Override
            public String getName() {
                return "DEFAULT"
            }

            @Override
            Map<String, Object> getProperties() {
                return [(OracleTransactional.ORACLE_PRIORITY): priority]
            }
        }
    }

    private static final class OracleTxFixture {
        final Connection connection
        final DefaultConnectionStatus<Connection> status
        final DefaultTransactionStatus<Connection> txStatus

        OracleTxFixture(Connection connection, DefaultConnectionStatus<Connection> status, DefaultTransactionStatus<Connection> txStatus) {
            this.connection = connection
            this.status = status
            this.txStatus = txStatus
        }
    }
}
