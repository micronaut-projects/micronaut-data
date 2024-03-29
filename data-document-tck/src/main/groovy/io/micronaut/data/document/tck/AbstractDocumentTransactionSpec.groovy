package io.micronaut.data.document.tck

import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.tck.repositories.BookRepository
import io.micronaut.data.document.tck.services.TxBookService
import io.micronaut.data.document.tck.services.TxEventsService
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
abstract class AbstractDocumentTransactionSpec extends Specification implements TestPropertyProvider {

    private static final Long CONNECTIONS = 1000

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.builder()
            .properties(getProperties() + [(TxEventsService.BOOK_REPOSITORY_CLASS_PROPERTY): getBookRepositoryClass().name])
            .packages(getPackages())
            .start()

    abstract Class<? extends BookRepository> getBookRepositoryClass();

    String[] getPackages() {
        return null
    }

    protected abstract TransactionOperations getTransactionOperations();

    protected abstract Runnable getNoTxCheck();

    TxBookService getBookService() {
        return context.getBean(TxBookService)
    }

    void cleanup() {
        bookService.cleanup()
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
            bookService.bookAddedInReadOnlyTransaction()
        then:
            def e = thrown(Exception)
            cannotInsertInReadOnlyTx(e)
    }

    void "test read only transaction adding book in inner transaction"() {
        if (!supportsReadOnlyFlag()) {
            return
        }
        when:
            bookService.readOnlyTxCallingAddingBookInAnotherTransaction()
        then:
            def e = thrown(Exception)
            cannotInsertInReadOnlyTx(e)
    }

    void "test book added in never propagation"() {
        when:
            bookService.bookAddedInNeverPropagation(getNoTxCheck())
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional() == 1
            } else {
                assert bookService.countBooksTransactional() == 0
            }
    }

    void "test book added in inner never propagation"() {
        when:
            bookService.bookAddedInInnerNeverPropagation(getNoTxCheck())
        then:
            def e = thrown(Exception)
            e.message == "Existing transaction found for transaction marked with propagation 'never'"
            bookService.countBooksTransactional() == 0
    }

    void "test book added in not-supported propagation"() {
        when:
            bookService.bookAddedInNoSupportedPropagation(getNoTxCheck())
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional() == 1
            } else {
                assert bookService.countBooksTransactional() == 0
            }
    }

    void "test book added in not-supported propagation and failed"() {
        when:
            bookService.bookAddedInNoSupportedPropagationAndFailed(getNoTxCheck())
        then:
            thrown(Exception)
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional() == 1
            } else {
                assert bookService.countBooksTransactional() == 0
            }
    }

    void "test book added in inner not-supported propagation and failed with exception suppressed"() {
        when:
            bookService.bookAddedInInnerNoSupportedPropagationFailedAndExceptionSuppressed(getNoTxCheck())
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional() == 1
            } else {
                assert bookService.countBooksTransactional() == 0
            }
    }

    void "test book added in inner not-supported propagation"() {
        when:
            bookService.bookAddedInInnerNoSupportedPropagation(getNoTxCheck())
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional() == 1
            } else {
                assert bookService.countBooksTransactional() == 0
            }
    }

    void "test mandatory transaction missing"() {
        when:
            bookService.mandatoryTransaction()
        then:
            def e = thrown(Exception)
            e.message == "No existing transaction found for transaction marked with propagation 'mandatory'"
    }

    void "test book is added in mandatory transaction"() {
        when:
            bookService.bookAddedInMandatoryTransaction()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test inner transaction with suppressed exception"() {
        when:
            bookService.innerTransactionHasSuppressedException()
        then:
            def e = thrown(Exception)
            e.message == "Transaction rolled back because it has been marked as rollback-only"
    }

    void "test inner requires new transaction with suppressed exception"() {
        when:
            bookService.innerRequiresNewTransactionHasSuppressedException()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test book is added in another requires new TX"() {
        when:
            bookService.bookIsAddedInAnotherRequiresNewTx()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test book is added in another requires new TX which if failing"() {
        when:
            bookService.bookIsAddedInAnotherRequiresNewTxWhichIsFailing()
        then:
            def e = thrown(IllegalStateException)
            e.message == "Big fail!"
        and:
            bookService.countBooksTransactional() == 0
    }

    void "test book is added in the main TX and another requires new TX is failing"() {
        when:
            bookService.bookIsAddedAndAnotherRequiresNewTxIsFailing()
        then:
            def e = thrown(IllegalStateException)
            e.message == "Big fail!"
        and:
            bookService.countBooksTransactional() == 0
    }

    void "test that connections are never exhausted 1"() {
        when:
            CONNECTIONS.times { bookService.bookIsAddedInTxMethod() }
        then:
            CONNECTIONS == bookService.countBooks()
    }

    void "test that connections are never exhausted 2"() {
        when:
            CONNECTIONS.times { bookService.bookIsAddedInAnotherRequiresNewTx() }
        then:
            CONNECTIONS == bookService.countBooks()
    }

    void "test that connections are never exhausted 3"() {
        when:
            CONNECTIONS.times { bookService.innerRequiresNewTransactionHasSuppressedException() }
        then:
            CONNECTIONS == bookService.countBooks()
    }

    void "test that connections are never exhausted 4"() {
        when:
            CONNECTIONS.times { bookService.bookAddedInMandatoryTransaction() }
        then:
            CONNECTIONS == bookService.countBooks()
    }

    void "test that connections are never exhausted 5"() {
        when:
            CONNECTIONS.times { bookService.bookAddedInInnerNoSupportedPropagation(getNoTxCheck()) }
        then:
            if (supportsModificationInNonTransaction()) {
                assert CONNECTIONS == bookService.countBooks()
            } else {
                assert 0L == bookService.countBooks()
            }
    }

    void "test that connections are never exhausted 6"() {
        when:
            CONNECTIONS.times { bookService.bookAddedInNeverPropagation(getNoTxCheck()) }
        then:
            if (supportsModificationInNonTransaction()) {
                assert CONNECTIONS == bookService.countBooks()
            } else {
                assert 0L == bookService.countBooks()
            }
    }

    void "test transactional events handling"() {
        given:
            TxEventsService txEventsService = context.getBean(TxEventsService)
        when: "an insert is performed in a transaction"
            txEventsService.insertWithTransaction()

        then: "The insert worked"
            txEventsService.lastEvent?.title() == "The Stand"
            txEventsService.countBooksTransactional() == 1

        when: "A transaction is rolled back"
            txEventsService.cleanLastEvent()
            txEventsService.insertAndRollback()

        then:
            def e = thrown(RuntimeException)
            e.message == 'Bad things happened'
            txEventsService.lastEvent == null
            txEventsService.countBooksTransactional() == 1


        when: "A transaction is rolled back"
            txEventsService.insertAndRollbackChecked()

        then:
            def e2 = thrown(Exception)
            e2.message == 'Bad things happened'
            txEventsService.lastEvent == null
            txEventsService.countBooksTransactional() == 1

        when: "A transaction is rolled back but the exception ignored"
            txEventsService.insertAndRollbackDontRollbackOn()

        then:
            thrown(IOException)
            if (supportsDontRollbackOn()) {
                assert txEventsService.countBooksTransactional() == 2
                assert txEventsService.lastEvent
            } else {
                assert txEventsService.countBooksTransactional() == 1
                assert txEventsService.lastEvent == null
            }
    }

    void "test TX managed"() {
        when:
            assert transactionOperations.findTransactionStatus().isEmpty()
            bookService.checkInTransaction({
                assert transactionOperations.findTransactionStatus().isPresent()
            })
            assert transactionOperations.findTransactionStatus().isEmpty()
        then:
            noExceptionThrown()
    }

}
