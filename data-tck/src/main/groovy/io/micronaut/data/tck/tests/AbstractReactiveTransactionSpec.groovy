package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.SimpleReactiveBookRepository
import io.micronaut.data.tck.services.TxBookService
import io.micronaut.data.tck.services.TxEventsService
import io.micronaut.data.tck.services.TxReactiveBookService
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractReactiveTransactionSpec extends Specification implements TestPropertyProvider {

    private static final Long CONNECTIONS = 1000

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.builder()
            .properties(getProperties() + [(TxReactiveBookService.BOOK_REPOSITORY_CLASS_PROPERTY): getBookRepositoryClass().name])
            .packages(getPackages())
            .start()

    abstract Class<? extends SimpleReactiveBookRepository> getBookRepositoryClass();

    String[] getPackages() {
        return null
    }

    TxReactiveBookService getBookService() {
        return context.getBean(TxReactiveBookService)
    }

    void cleanup() {
        bookService.cleanup().block()
    }

    boolean supportsNoTxProcessing() {
        return true
    }

    boolean supportsModificationInNonTransaction() {
        return true
    }

    boolean supportsDontRollbackOn() {
        return true
    }

    boolean supportsReadOnlyFlag() {
        return true
    }

    boolean cannotInsertInReadOnlyTx(Exception e) {
        return false
    }

    void "test book added in read only transaction"() {
        if (!supportsReadOnlyFlag()) {
            return
        }
        when:
            bookService.bookAddedInReadOnlyTransaction().block()
        then:
            def e = thrown(Exception)
            cannotInsertInReadOnlyTx(e)
    }

    void "test read only transaction adding book in inner transaction"() {
        if (!supportsReadOnlyFlag()) {
            return
        }
        when:
            bookService.readOnlyTxCallingAddingBookInAnotherTransaction().block()
        then:
            def e = thrown(Exception)
            cannotInsertInReadOnlyTx(e)
    }

    void "test book added in never propagation"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInNeverPropagation().block()
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional().block() == 1
            } else {
                assert bookService.countBooksTransactional().block() == 0
            }
    }

    void "test book added in inner never propagation"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInInnerNeverPropagation().block()
        then:
            def e = thrown(Exception)
            e.message == "Existing transaction found for transaction marked with propagation 'never'"
            bookService.countBooksTransactional().block() == 0
    }

    void "test book added in not-supported propagation"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInNoSupportedPropagation().block()
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional().block() == 1
            } else {
                assert bookService.countBooksTransactional().block() == 0
            }
    }

    void "test book added in not-supported propagation and failed"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInNoSupportedPropagationAndFailed().block()
        then:
            thrown(Exception)
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional().block() == 1
            } else {
                assert bookService.countBooksTransactional().block() == 0
            }
    }

    void "test book added in inner not-supported propagation and failed with exception suppressed"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInInnerNoSupportedPropagationFailedAndExceptionSuppressed().block()
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional().block() == 1
            } else {
                assert bookService.countBooksTransactional().block() == 0
            }
    }

    void "test book added in inner not-supported propagation"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInInnerNoSupportedPropagation()
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional().block() == 1
            } else {
                assert bookService.countBooksTransactional().block() == 0
            }
    }

    void "test mandatory transaction missing"() {
        when:
            bookService.mandatoryTransaction().block()
        then:
            def e = thrown(Exception)
            e.message == "No existing transaction found for transaction marked with propagation 'mandatory'"
    }

    void "test book is added in mandatory transaction"() {
        when:
            bookService.bookAddedInMandatoryTransaction().block()
        then:
            bookService.countBooksTransactional().block() == 1
    }

    void "test inner transaction with suppressed exception"() {
        when:
            bookService.innerTransactionHasSuppressedException().block()
        then:
            def e = thrown(Exception)
            e.message == "Transaction rolled back because it has been marked as rollback-only"
    }

    void "test inner transaction marked for rollback"() {
        when:
            bookService.innerTransactionMarkedForRollback().block()
        then:
            def e = thrown(Exception)
            e.message == "Transaction rolled back because it has been marked as rollback-only"
    }

    void "test transaction marked for rollback"() {
        when:
            bookService.saveAndMarkedForRollback().block()
        then:
            bookService.countBooksTransactional().block() == 0
    }

    void "test transaction marked for rollback 2"() {
        when:
            bookService.saveAndMarkedForRollback2().block()
        then:
            bookService.countBooksTransactional().block() == 0
    }

    void "test inner requires new transaction with suppressed exception"() {
        when:
            bookService.innerRequiresNewTransactionHasSuppressedException().block()
        then:
            bookService.countBooksTransactional().block() == 1
    }

    void "test book is added in another requires new TX"() {
        when:
            bookService.bookIsAddedInAnotherRequiresNewTx().block()
        then:
            bookService.countBooksTransactional().block() == 1
    }

    void "test book is added in another requires new TX which if failing"() {
        when:
            bookService.bookIsAddedInAnotherRequiresNewTxWhichIsFailing().block()
        then:
            def e = thrown(IllegalStateException)
            e.message == "Big fail!"
        and:
            bookService.countBooksTransactional().block() == 0
    }

    void "test book is added in the main TX and another requires new TX is failing"() {
        when:
            bookService.bookIsAddedAndAnotherRequiresNewTxIsFailing().block()
        then:
            def e = thrown(IllegalStateException)
            e.message == "Big fail!"
        and:
            bookService.countBooksTransactional().block() == 0
    }

    void "test that connections are never exhausted 1"() {
        when:
            CONNECTIONS.times { bookService.bookIsAddedInTxMethod().block() }
        then:
            CONNECTIONS == bookService.countBooks().block()
    }

    void "test that connections are never exhausted 2"() {
        when:
            CONNECTIONS.times { bookService.bookIsAddedInAnotherRequiresNewTx().block() }
        then:
            CONNECTIONS == bookService.countBooks().block()
    }

    void "test that connections are never exhausted 3"() {
        when:
            CONNECTIONS.times { bookService.innerRequiresNewTransactionHasSuppressedException().block() }
        then:
            CONNECTIONS == bookService.countBooks().block()
    }

    void "test that connections are never exhausted 4"() {
        when:
            CONNECTIONS.times { bookService.bookAddedInMandatoryTransaction().block() }
        then:
            CONNECTIONS == bookService.countBooks().block()
    }

    void "test that connections are never exhausted 5"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            CONNECTIONS.times { bookService.bookAddedInInnerNoSupportedPropagation().block() }
        then:
            if (supportsModificationInNonTransaction()) {
                assert CONNECTIONS == bookService.countBooks().block()
            } else {
                assert 0L == bookService.countBooks().block()
            }
    }

    void "test that connections are never exhausted 6"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            CONNECTIONS.times { bookService.bookAddedInNeverPropagation().block() }
        then:
            if (supportsModificationInNonTransaction()) {
                assert CONNECTIONS == bookService.countBooks().block()
            } else {
                assert 0L == bookService.countBooks().block()
            }
    }

//    void "test transactional events handling"() {
//        given:
//            TxEventsService txEventsService = context.getBean(TxEventsService)
//        when: "an insert is performed in a transaction"
//            txEventsService.insertWithTransaction()
//
//        then: "The insert worked"
//            txEventsService.lastEvent?.title() == "The Stand"
//            txEventsService.countBooksTransactional() == 1
//
//        when: "A transaction is rolled back"
//            txEventsService.cleanLastEvent()
//            txEventsService.insertAndRollback()
//
//        then:
//            def e = thrown(RuntimeException)
//            e.message == 'Bad things happened'
//            txEventsService.lastEvent == null
//            txEventsService.countBooksTransactional() == 1
//
//
//        when: "A transaction is rolled back"
//            txEventsService.insertAndRollbackChecked()
//
//        then:
//            def e2 = thrown(Exception)
//            e2.message == 'Bad things happened'
//            txEventsService.lastEvent == null
//            txEventsService.countBooksTransactional() == 1
//
//        when: "A transaction is rolled back but the exception ignored"
//            txEventsService.insertAndRollbackDontRollbackOn()
//
//        then:
//            thrown(IOException)
//            if (supportsDontRollbackOn()) {
//                assert txEventsService.countBooksTransactional() == 2
//                assert txEventsService.lastEvent
//            } else {
//                assert txEventsService.countBooksTransactional() == 1
//                assert txEventsService.lastEvent == null
//            }
//    }
//
//    void "test TX managed"() {
//        when:
//            assert transactionOperations.findTransactionStatus().isEmpty()
//            bookService.checkInTransaction({
//                assert transactionOperations.findTransactionStatus().isPresent()
//            })
//            assert transactionOperations.findTransactionStatus().isEmpty()
//        then:
//            noExceptionThrown()
//    }

}
