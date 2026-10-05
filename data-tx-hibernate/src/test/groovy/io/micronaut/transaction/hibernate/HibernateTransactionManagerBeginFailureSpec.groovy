package io.micronaut.transaction.hibernate

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.impl.DefaultTransactionStatus
import org.hibernate.Session
import org.hibernate.Transaction
import spock.lang.Specification

class HibernateTransactionManagerBeginFailureSpec extends Specification {

    Session session = Mock(Session)

    HibernateTransactionManager transactionManager = new HibernateTransactionManager(
        Mock(ConnectionOperations),
        Mock(SynchronousConnectionManager)
    )

    DefaultTransactionStatus<Session> status = DefaultTransactionStatus.newTx(
        Stub(ConnectionStatus) {
            getConnection() >> session
            isNew() >> true
        },
        TransactionDefinition.DEFAULT,
        transactionManager
    )

    def "transaction not started before the begin failure is not rolled back"() {
        when:
            transactionManager.doRollbackAfterBeginFailure(status)
        then:
            noExceptionThrown()
    }

    def "started transaction is rolled back after the begin failure"() {
        given:
            def transaction = Mock(Transaction)
            status.setTransaction(transaction)
        when:
            transactionManager.doRollbackAfterBeginFailure(status)
        then:
            1 * transaction.rollback()
    }
}
