package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.services.TxBookService
import io.micronaut.data.tck.services.TxEventsService
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractTransactionSpec extends Specification implements TestPropertyProvider {

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

    protected abstract ConnectionOperations getConnectionOperations();

    protected abstract Runnable getNoTxCheck();

    TxBookService getBookService() {
        def service = context.getBean(TxBookService)
        service.transactionManager = getTransactionOperations()
        service.connectionOperations = getConnectionOperations()
        return service
    }

    void cleanup() {
        bookService.cleanup()
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

    boolean supportsNestedTx() {
        return true
    }

    boolean cannotInsertInReadOnlyTx(Exception e) {
        return false
    }

    boolean failsInsertInReadOnlyTx() {
        return false
    }

    void "connectable with nested transaction"() {
        try {
            when:
                bookService.bookAddedInConnectableNestedTransaction()
            then:
                bookService.countBooksTransactional() == 1
        } catch (NoClassDefFoundError e) {
            // Avoid Spring Integration failing with Hibernate 6
        }
    }

    void "custom name transaction"() {
        when:
            bookService.bookAddedCustomNamedTransaction(new Runnable() {
                @Override
                void run() {
                    if (getTransactionOperations().findTransactionStatus().get().getTransactionDefinition().getName() != "MyTx") {
                        throw new IllegalStateException("Expected a custom TX name!")
                    }
                }
            })
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test book added in read only transaction throws error"() {
        if (!supportsReadOnlyFlag() || !failsInsertInReadOnlyTx()) {
            return
        }
        when:
            bookService.bookAddedInReadOnlyTransaction()
        then:
            def e = thrown(Exception)
            cannotInsertInReadOnlyTx(e)
    }

    void "test book added in read only transaction not throwing error"() {
        if (!supportsReadOnlyFlag() || failsInsertInReadOnlyTx()) {
            return
        }
        when:
        bookService.bookAddedInReadOnlyTransaction()
        then:
        noExceptionThrown()
        bookService.countBooksTransactional() == 1
    }

    void "test read only transaction adding book in inner transaction throws error"() {
        if (!supportsReadOnlyFlag() || !failsInsertInReadOnlyTx()) {
            return
        }
        when:
            bookService.readOnlyTxCallingAddingBookInAnotherTransaction()
        then:
            def e = thrown(Exception)
            cannotInsertInReadOnlyTx(e)
    }

    void "test read only transaction adding book in inner transaction not throwing error"() {
        if (!supportsReadOnlyFlag() || failsInsertInReadOnlyTx()) {
            return
        }
        when:
        bookService.readOnlyTxCallingAddingBookInAnotherTransaction()
        then:
        noExceptionThrown()
        bookService.countBooksTransactional() == 1
    }

    void "test book added in never propagation"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInNeverPropagation(getNoTxCheck())
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional() == 1
            } else {
                assert bookService.countBooksTransactional() == 0
            }
    }

    void "test book added in never propagation sync"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInNeverPropagationSync(getNoTxCheck())
        then:
            if (supportsModificationInNonTransaction()) {
                assert bookService.countBooksTransactional() == 1
            } else {
                assert bookService.countBooksTransactional() == 0
            }
    }

    void "test book added in inner never propagation"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInInnerNeverPropagation(getNoTxCheck())
        then:
            def e = thrown(Exception)
            e.message == "Existing transaction found for transaction marked with propagation 'never'"
            bookService.countBooksTransactional() == 0
    }

    void "test book added in inner never propagation sync"() {
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            bookService.bookAddedInInnerNeverPropagationSync(getNoTxCheck())
        then:
            def e = thrown(Exception)
            e.message == "Existing transaction found for transaction marked with propagation 'never'"
            transactionOperations.findTransactionStatus().isEmpty()
            bookService.countBooksTransactional() == 0
    }

    void "test book added in not-supported propagation"() {
        if (!supportsNoTxProcessing()) {
            return
        }
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
        if (!supportsNoTxProcessing()) {
            return
        }
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
        if (!supportsNoTxProcessing()) {
            return
        }
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
        if (!supportsNoTxProcessing()) {
            return
        }
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

    void "test mandatory transaction missing sync"() {
        when:
            bookService.mandatoryTransactionSync()
        then:
            def e = thrown(Exception)
            e.message == "No existing transaction found for transaction marked with propagation 'mandatory'"
            transactionOperations.findTransactionStatus().isEmpty()
    }

    void "test book is added in mandatory transaction"() {
        when:
            bookService.bookAddedInMandatoryTransaction()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test book is added in mandatory transaction sync"() {
        when:
            bookService.bookAddedInMandatoryTransactionSync()
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

    void "test inner transaction with suppressed exception sync"() {
        when:
            bookService.innerTransactionHasSuppressedExceptionSync()
        then:
            def e = thrown(Exception)
            e.message == "Transaction rolled back because it has been marked as rollback-only"
            transactionOperations.findTransactionStatus().isEmpty()
    }

    void "test inner transaction with suppressed exception sync2"() {
        when:
            bookService.innerTransactionHasSuppressedExceptionSync2()
        then:
            def e = thrown(Exception)
            e.message == "Transaction rolled back because it has been marked as rollback-only"
            transactionOperations.findTransactionStatus().isEmpty()
    }

    void "test inner transaction marked for rollback"() {
        when:
            bookService.innerTransactionMarkedForRollback {
                getTransactionOperations().findTransactionStatus().get().setRollbackOnly()
            }
        then:
            def e = thrown(Exception)
            e.message == "Transaction rolled back because it has been marked as rollback-only"
    }

    void "test transaction marked for rollback"() {
        when:
            bookService.saveAndMarkedForRollback {
                getTransactionOperations().findTransactionStatus().get().setRollbackOnly()
            }
        then:
            bookService.countBooksTransactional() == 0
    }

    void "test transaction marked for rollback 2"() {
        when:
            bookService.saveAndMarkedForRollback2 {
                getTransactionOperations().findTransactionStatus().get().setRollbackOnly()
            }
        then:
            bookService.countBooksTransactional() == 0
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

    void "test book is added in another requires new TX spec"() {
        when:
            bookService.bookIsAddedInAnotherRequiresNewTxSync()
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

    void "test book is added in the main TX and another requires new TX is failing sync"() {
        when:
            bookService.bookIsAddedAndAnotherRequiresNewTxIsFailingSync()
        then:
            def e = thrown(IllegalStateException)
            e.message == "Big fail!"
        and:
            bookService.countBooksTransactional() == 0
    }

    void "test book is added in nested TX"() {
        if (!supportsNestedTx()) {
            return
        }
        when:
            bookService.bookAddedInNestedTransaction()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test book is added in nested TX sync"() {
        if (!supportsNestedTx()) {
            return
        }
        when:
            bookService.bookAddedInNestedTransactionSync()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test book is added in another nested TX"() {
        if (!supportsNestedTx()) {
            return
        }
        when:
            bookService.bookAddedInAnotherNestedTransaction()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test book is added in another nested TX sync"() {
        if (!supportsNestedTx()) {
            return
        }
        when:
            bookService.bookAddedInAnotherNestedTransactionSync()
        then:
            bookService.countBooksTransactional() == 1
    }

    void "test that connections are never exhausted 1"() {
        when:
            CONNECTIONS.times { bookService.bookIsAddedInTxMethod() }
        then:
            CONNECTIONS == bookService.countBooks()
    }

    void "test that connections are never exhausted 2"() {
        when:
            CONNECTIONS.times { bookService.bookIsAddedInAnotherRequiresNewTxSync() }
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
        if (!supportsNoTxProcessing()) {
            return
        }
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
        if (!supportsNoTxProcessing()) {
            return
        }
        when:
            CONNECTIONS.times { bookService.bookAddedInNeverPropagation(getNoTxCheck()) }
        then:
            if (supportsModificationInNonTransaction()) {
                assert CONNECTIONS == bookService.countBooks()
            } else {
                assert 0L == bookService.countBooks()
            }
    }

    void "test that connections are never exhausted 7"() {
        if (!supportsNestedTx()) {
            return
        }
        when:
            CONNECTIONS.times { bookService.bookAddedInNestedTransaction() }
        then:
            CONNECTIONS == bookService.countBooks()
    }

    void "test that connections are never exhausted 8"() {
        if (!supportsNestedTx()) {
            return
        }
        when:
            CONNECTIONS.times { bookService.bookAddedInNestedTransactionSync() }
        then:
            CONNECTIONS == bookService.countBooks()
    }

    void "test that connections are never exhausted 9"() {
        when:
            CONNECTIONS.times {
                try {
                    bookService.innerTransactionHasSuppressedExceptionSync()
                    assert false
                } catch (Exception e) {
                    assert e.message == "Transaction rolled back because it has been marked as rollback-only"
                }
            }
        then:
            bookService.countBooks() == 0
    }

    void "test transactional events handling"() {
        given:
            TxEventsService txEventsService = context.getBean(TxEventsService)
        when: "an insert is performed in a transaction"
            txEventsService.insertWithTransaction()

        then: "The insert worked"
            txEventsService.lastEvent?.title() == "The Stand"
            txEventsService.countBooksTransactional() == 1
            txEventsService.events == ["BEFORE COMMIT: false", "BEFORE COMPLETION", "AFTER COMMIT", "AFTER COMPLETION: COMMITTED"]

        when: "A transaction is rolled back"
            txEventsService.cleanup()
            txEventsService.insertAndRollback()

        then:
            def e = thrown(RuntimeException)
            e.message == 'Bad things happened'
            txEventsService.lastEvent == null
            txEventsService.countBooksTransactional() == 0
            txEventsService.events == ["BEFORE COMPLETION", "AFTER COMPLETION: ROLLED_BACK"]

        when: "A transaction is rolled back"
            txEventsService.cleanup()
            txEventsService.insertAndRollbackWithOuterTransaction()

        then:
            e = thrown(RuntimeException)
            e.message == 'Bad things happened'
            txEventsService.lastEvent == null
            txEventsService.countBooksTransactional() == 0
            txEventsService.events == ["ENTER INNER", "OUTER BEFORE COMPLETION", "BEFORE COMPLETION", "OUTER AFTER COMPLETION: ROLLED_BACK", "AFTER COMPLETION: ROLLED_BACK"]

        when: "A transaction is rolled back"
            txEventsService.cleanup()
            txEventsService.insertAndRollbackChecked()

        then:
            def e2 = thrown(Exception)
            e2.message == 'Bad things happened'
            txEventsService.lastEvent == null
            txEventsService.countBooksTransactional() == 0
            txEventsService.events == ["BEFORE COMPLETION", "AFTER COMPLETION: ROLLED_BACK"]

        when: "A transaction is rolled back"
            txEventsService.cleanup()
            txEventsService.insertAndRollbackCheckedWithOuterTransaction()

        then:
            e2 = thrown(Exception)
            e2.message == 'Bad things happened'
            txEventsService.lastEvent == null
            txEventsService.countBooksTransactional() == 0
            txEventsService.events == ["ENTER INNER", "OUTER BEFORE COMPLETION", "BEFORE COMPLETION", "OUTER AFTER COMPLETION: ROLLED_BACK", "AFTER COMPLETION: ROLLED_BACK"]

        when: "A transaction is rolled back but the exception ignored"
            txEventsService.cleanup()
            txEventsService.insertAndRollbackDontRollbackOn()

        then:
            thrown(IOException)
            if (supportsDontRollbackOn()) {
                assert txEventsService.countBooksTransactional() == 1
                assert txEventsService.lastEvent
            } else {
                assert txEventsService.countBooksTransactional() == 0
                assert txEventsService.lastEvent == null
            }

        when:
            txEventsService.cleanup()
            txEventsService.insertWithOuterTransaction()
        then:
            noExceptionThrown()
            txEventsService.lastEvent?.title() == "The Stand"
            txEventsService.countBooksTransactional() == 1
            txEventsService.events == ["ENTER INNER", "EXIT INNER", "OUTER BEFORE COMMIT: false", "BEFORE COMMIT: false", "OUTER BEFORE COMPLETION", "BEFORE COMPLETION", "OUTER AFTER COMMIT", "AFTER COMMIT", "OUTER AFTER COMPLETION: COMMITTED", "AFTER COMPLETION: COMMITTED"]

        when:
            txEventsService.cleanup()
            txEventsService.insertWithOuterNewTransaction()
        then:
            noExceptionThrown()
            txEventsService.lastEvent?.title() == "The Stand"
            txEventsService.countBooksTransactional() == 1
            txEventsService.events == ["ENTER INNER", "BEFORE COMMIT: false", "BEFORE COMPLETION", "AFTER COMMIT", "AFTER COMPLETION: COMMITTED", "EXIT INNER", "OUTER BEFORE COMMIT: false", "OUTER BEFORE COMPLETION", "OUTER AFTER COMMIT", "OUTER AFTER COMPLETION: COMMITTED"]
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
