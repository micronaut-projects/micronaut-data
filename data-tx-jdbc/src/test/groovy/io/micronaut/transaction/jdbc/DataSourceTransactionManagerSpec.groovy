package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException
import spock.lang.Specification

import javax.sql.DataSource

class DataSourceTransactionManagerSpec extends Specification {

    def "plain JDBC manager rejects Oracle sessionless propagation before transactional work"(TransactionDefinition.Propagation propagation) {
        given:
        def txManager = newTxManager()

        when:
        txManager.execute(definition(propagation), { status -> null })

        then:
        def e = thrown(TransactionSuspensionNotSupportedException)
        e.message == "Propagation '" + propagation + "' requires Oracle sessionless transaction support"

        where:
        propagation << [
            TransactionDefinition.Propagation.SUSPEND,
            TransactionDefinition.Propagation.REQUIRES_SUSPENDED
        ]
    }

    def "plain JDBC manager rejects Oracle sessionless propagation before programmatic transaction creation"(TransactionDefinition.Propagation propagation) {
        given:
        def txManager = newTxManager()

        when:
        txManager.getTransaction(definition(propagation))

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
