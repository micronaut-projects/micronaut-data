package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

class DataSourceTransactionManagerSpec extends Specification {

    def "plain JDBC manager rejects Oracle sessionless propagation before JDBC begin"(TransactionDefinition.Propagation propagation) {
        given:
        def txManager = newTxManager()
        def connection = Mock(Connection)
        def connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.named("test"), true, null)
        def txStatus = DefaultTransactionStatus.newTx(connectionStatus, definition(propagation), txManager)

        when:
        txManager.doBegin(txStatus)

        then:
        def e = thrown(TransactionSuspensionNotSupportedException)
        e.message == "Propagation '" + propagation + "' requires Oracle sessionless transaction support"
        0 * connection._

        where:
        propagation << [
            TransactionDefinition.Propagation.SUSPEND,
            TransactionDefinition.Propagation.REQUIRES_SUSPENDED
        ]
    }

    def "plain JDBC manager rejects Oracle sessionless propagation before joining an existing transaction"(TransactionDefinition.Propagation propagation) {
        given:
        def txManager = newTxManager()
        def connection = Mock(Connection)
        def connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.named("test"), true, null)
        def existingTransaction = DefaultTransactionStatus.newTx(connectionStatus, definition(TransactionDefinition.Propagation.REQUIRED), txManager)

        when:
        txManager.createExistingTransactionStatus(definition(propagation), existingTransaction)

        then:
        def e = thrown(TransactionSuspensionNotSupportedException)
        e.message == "Propagation '" + propagation + "' requires Oracle sessionless transaction support"
        0 * connection._

        where:
        propagation << [
            TransactionDefinition.Propagation.SUSPEND,
            TransactionDefinition.Propagation.REQUIRES_SUSPENDED
        ]
    }

    def "plain JDBC manager rejects Oracle sessionless propagation before resolving a connection definition"(TransactionDefinition.Propagation propagation) {
        given:
        def txManager = newTxManager()

        when:
        txManager.getConnectionDefinition(definition(propagation))

        then:
        def e = thrown(TransactionSuspensionNotSupportedException)
        e.message == "Propagation '" + propagation + "' requires Oracle sessionless transaction support"

        where:
        propagation << [
            TransactionDefinition.Propagation.SUSPEND,
            TransactionDefinition.Propagation.REQUIRES_SUSPENDED
        ]
    }

    private DataSourceTransactionManager newTxManager() {
        new DataSourceTransactionManager(
            Mock(DataSource),
            Mock(ConnectionOperations),
            Mock(SynchronousConnectionManager)
        )
    }

    private static TransactionDefinition definition(TransactionDefinition.Propagation propagation) {
        new TransactionDefinition() {
            @Override
            String getName() {
                "test"
            }

            @Override
            TransactionDefinition.Propagation getPropagationBehavior() {
                propagation
            }
        }
    }
}
