package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionDefinition
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

class OwnerTransactionAutoCommitSpec extends Specification {

    void "joining the owner's transaction of a supplied connection works whatever its auto-commit mode reports"() {
        given:
        def manager = new DataSourceTransactionManager(Mock(DataSource), Mock(ConnectionOperations), null)
        Connection connection = Mock() {
            getAutoCommit() >> {
                if (failure) {
                    throw new SQLException("closed")
                }
                autoCommit
            }
        }
        def supplied = new DefaultConnectionStatus.SuppliedConnection(true)
        def status = new DefaultConnectionStatus<Connection>(connection, ConnectionDefinition.DEFAULT, false, Mock(ConnectionOperations), supplied)

        when: "auto-commit on is only warned about, and a failure to read it does not stop the join"
        def transaction = manager.createOwnerTransactionStatus(status, TransactionDefinition.DEFAULT, supplied)

        then:
        !transaction.newTransaction
        transaction.connection.is(connection)

        where:
        autoCommit | failure
        true       | false
        false      | false
        false      | true
    }
}
