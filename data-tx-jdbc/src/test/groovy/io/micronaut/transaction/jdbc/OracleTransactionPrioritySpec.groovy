package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.DefaultConnectionDefinition
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.exceptions.CannotCreateTransactionException
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import io.micronaut.transaction.support.OracleTransactionPriorityStatements
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types

/**
 * Verifies that Oracle transaction priority is applied at transaction begin, reading the previous priority of the session,
 * and that the previous priority is restored after completion when @OracleTransactional is present.
 */
class OracleTransactionPrioritySpec extends Specification {

    def "applies Oracle txn_priority and restores the previous priority of the session"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.LOW)
        def call = Mock(CallableStatement)
        def stmt = Mock(Statement)

        when: "Beginning the transaction"
        txManager.doBegin(fixture.txStatus)

        then: "Priority is set to LOW, reading the previous priority"
        1 * fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        1 * call.setString(1, 'LOW')
        1 * call.registerOutParameter(2, Types.VARCHAR)
        1 * call.execute()
        1 * call.getString(2) >> 'MEDIUM'
        1 * call.close()

        when: "Execution completes"
        fixture.status.complete()

        then: "The previous priority is restored"
        1 * fixture.connection.createStatement() >> stmt
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="MEDIUM"')
    }

    def "resets Oracle txn_priority to HIGH when the previous priority is unknown"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.LOW)
        def call = Mock(CallableStatement)
        def stmt = Mock(Statement)
        fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        call.getString(2) >> previous

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then:
        1 * fixture.connection.createStatement() >> stmt
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="HIGH"')

        where:
        previous << [null, 'unknown']
    }

    def "ignores ORA-02248 while setting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.MEDIUM)
        def call = Mock(CallableStatement)
        fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        call.execute() >> { throw new SQLException('ORA-02248: invalid option for ALTER SESSION\nORA-06512: at line 9', '42000', 2248) }

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then: "Nothing is reset"
        noExceptionThrown()
        0 * fixture.connection.createStatement()
    }

    def "ignores chained ORA-02248 while setting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.MEDIUM)
        def call = Mock(CallableStatement)
        fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        def exception = new SQLException('Statement failed', '42000', 0)
        exception.setNextException(new SQLException('ORA-02248: invalid option for ALTER SESSION', '42000', 2248))
        call.execute() >> { throw exception }

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then:
        noExceptionThrown()
        0 * fixture.connection.createStatement()
    }

    def "rethrows non-ORA-02248 failure while setting Oracle txn_priority"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.MEDIUM)
        def call = Mock(CallableStatement)
        fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        call.execute() >> { throw new SQLException('ORA-01031: insufficient privileges', '42000', 1031) }

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
        def call = Mock(CallableStatement)
        def stmt = Mock(Statement)
        fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        fixture.connection.createStatement() >> stmt
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
        def call = Mock(CallableStatement)
        def stmt = Mock(Statement)
        fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        call.getString(2) >> 'MEDIUM'
        fixture.connection.createStatement() >> stmt
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="MEDIUM"') >> { throw new SQLException('ORA-01031: insufficient privileges', '42000', 1031) }

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.status.complete()

        then:
        def e = thrown(TransactionSystemException)
        e.cause instanceof SQLException
        e.cause.errorCode == 1031
    }

    def "the priority is reset when the transaction completes, also on a connection of an outer scope"() {
        given:
        def txManager = newTxManager()
        def fixture = newOracleTxFixture(txManager, OracleTransactional.Priority.LOW)
        def call = Mock(CallableStatement)
        def stmt = Mock(Statement)
        fixture.connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS) >> call
        call.getString(2) >> 'MEDIUM'
        fixture.connection.createStatement() >> stmt
        // The connection is owned by an outer scope: its synchronizations run when the transaction completes
        fixture.txStatus.bindConnectionSynchronizationsToTransaction()

        when:
        txManager.doBegin(fixture.txStatus)
        fixture.txStatus.cleanupAfterCompletion()

        then: "Reset when the transaction completes"
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="MEDIUM"')

        when: "The outer scope completes"
        fixture.status.complete()

        then: "Not reset again"
        0 * stmt.executeUpdate(_)
    }

    def "no priority applied for non-Oracle databases"() {
        given:
        def dataSource = Mock(DataSource)
        def connection = Mock(Connection)
        def meta = Mock(DatabaseMetaData)
        ConnectionDefinition connDef = new DefaultConnectionDefinition("test")
        def status = new DefaultConnectionStatus<>(connection, connDef, true, null)
        def txDef = createWithPriority("invalid")
        def txManager = new DataSourceTransactionManager(dataSource, Mock(io.micronaut.data.connection.ConnectionOperations), Mock(io.micronaut.data.connection.SynchronousConnectionManager), oracleListeners())
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

        then: "No ALTER SESSION is executed and the invalid Oracle-specific value is ignored"
        noExceptionThrown()
        0 * connection.createStatement()
        0 * connection.prepareCall(_)
    }

    def "recognizes priority rollback only from an Oracle JDBC connection"() {
        given:
        def connection = Mock(Connection)
        def metadata = Mock(DatabaseMetaData)
        def exception = new SQLException('ORA-63302: transaction must be rolled back', '99999', 63302)

        when:
        def result = DataSourceTransactionManager.isOraclePriorityRollback(connection, exception)

        then:
        1 * connection.getMetaData() >> metadata
        1 * metadata.getDatabaseProductName() >> 'Oracle'
        result

        when:
        result = DataSourceTransactionManager.isOraclePriorityRollback(connection, exception)

        then:
        1 * connection.getMetaData() >> metadata
        1 * metadata.getDatabaseProductName() >> 'PostgreSQL'
        !result
    }

    def "does not inspect JDBC metadata for an unrelated failure"() {
        given:
        def connection = Mock(Connection)

        when:
        def result = DataSourceTransactionManager.isOraclePriorityRollback(connection, new RuntimeException('ORA-63302'))

        then:
        !result
        0 * connection.getMetaData()
    }

    private DataSourceTransactionManager newTxManager() {
        new DataSourceTransactionManager(Mock(DataSource), Mock(io.micronaut.data.connection.ConnectionOperations), Mock(io.micronaut.data.connection.SynchronousConnectionManager), oracleListeners())
    }

    private static List oracleListeners() {
        [new OracleTransactionPriorityTransactionExecutionListener()]
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

    static TransactionDefinition createWithPriority(Object priority) {
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
