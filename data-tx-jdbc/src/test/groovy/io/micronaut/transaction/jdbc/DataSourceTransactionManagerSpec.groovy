package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException
import io.micronaut.transaction.support.DefaultTransactionDefinition
import spock.lang.Specification

import javax.sql.DataSource

class DataSourceTransactionManagerSpec extends Specification {

    def "plain JDBC manager rejects Oracle sessionless mode before transactional work"(OracleTransactional.Sessionless mode) {
        given:
        def txManager = newTxManager()

        when:
        txManager.execute(definition(mode), { status -> null })

        then:
        def e = thrown(TransactionSuspensionNotSupportedException)
        e.message == "Oracle sessionless transaction mode '" + mode + "' requires Oracle sessionless transaction support"

        where:
        mode << [
            OracleTransactional.Sessionless.SUSPEND,
            OracleTransactional.Sessionless.REQUIRES_SUSPENDED
        ]
    }

    def "plain JDBC manager rejects Oracle sessionless mode before programmatic transaction creation"(OracleTransactional.Sessionless mode) {
        given:
        def txManager = newTxManager()

        when:
        txManager.getTransaction(definition(mode))

        then:
        def e = thrown(TransactionSuspensionNotSupportedException)
        e.message == "Oracle sessionless transaction mode '" + mode + "' requires Oracle sessionless transaction support"

        where:
        mode << [
            OracleTransactional.Sessionless.SUSPEND,
            OracleTransactional.Sessionless.REQUIRES_SUSPENDED
        ]
    }

    private DataSourceTransactionManager newTxManager() {
        new DataSourceTransactionManager(
            Mock(DataSource),
            Mock(ConnectionOperations),
            Mock(SynchronousConnectionManager)
        )
    }

    private static TransactionDefinition definition(OracleTransactional.Sessionless mode) {
        def definition = new DefaultTransactionDefinition()
        definition.setName("test")
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, mode)
        definition
    }
}
