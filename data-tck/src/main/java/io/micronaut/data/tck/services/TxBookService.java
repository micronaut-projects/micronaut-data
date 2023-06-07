package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.transaction.annotation.TransactionalAdvice;
import jakarta.inject.Singleton;

import javax.transaction.Transactional;

@Requires(property = AbstractBookService.BOOK_REPOSITORY_CLASS_PROPERTY)
@Singleton
public class TxBookService extends AbstractBookService {

    public TxBookService(ApplicationContext beanContext) {
        super(beanContext);
    }

    @TransactionalAdvice(readOnly = true)
    public void bookAddedInReadOnlyTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @TransactionalAdvice(readOnly = true)
    public void readOnlyTxCallingAddingBookInAnotherTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Transactional
    protected void addBook() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Transactional
    public void bookAddedInMandatoryTransaction() {
        mandatoryTransaction();
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void bookAddedInNoSupportedPropagation(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
    }

    @Transactional(Transactional.TxType.NEVER)
    public void bookAddedInNeverPropagation(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
    }

    @Transactional
    public void bookAddedInInnerNeverPropagation(Runnable noTxCheck) {
        bookAddedInNeverPropagation(noTxCheck);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void bookAddedInNoSupportedPropagationAndFailed(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
        throw new IllegalStateException("Big fail!");
    }

    @Transactional
    public void bookAddedInInnerNoSupportedPropagation(Runnable noTxCheck) {
        bookAddedInNoSupportedPropagation(noTxCheck);
    }

    @Transactional
    public void bookAddedInInnerNoSupportedPropagationFailedAndExceptionSuppressed(Runnable noTxCheck) {
        try {
            bookAddedInNoSupportedPropagationAndFailed(noTxCheck);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void mandatoryTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Transactional
    public void checkInTransaction(Runnable runnable) {
        runnable.run();
    }

    @Transactional
    public void bookIsAddedInTxMethod() {
        bookRepository.save(newBook("Toys"));
    }

    @Transactional
    public void bookIsAddedInAnotherRequiresNewTx() {
        addBookRequiresNew();
    }

    @Transactional
    public void bookIsAddedInAnotherRequiresNewTxWhichIsFailing() {
        addBookRequiresNewFailing();
    }

    @Transactional
    public void bookIsAddedAndAnotherRequiresNewTxIsFailing() {
        bookRepository.save(newBook("Book1"));
        transactionRequiresNewFailing();
    }

    @Transactional
    public void innerTransactionHasSuppressedException() {
        try {
            transactionFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @Transactional
    public void innerTransactionMarkedForRollback(Runnable markForRollback) {
        transactionMarkedForRollback(markForRollback);
        bookRepository.save(newBook("Book1"));
    }

    @Transactional
    public void saveAndMarkedForRollback(Runnable markForRollback) {
        bookRepository.save(newBook("Book1"));
        markForRollback.run();
    }

    @Transactional
    public void saveAndMarkedForRollback2(Runnable markForRollback) {
        markForRollback.run();
        bookRepository.save(newBook("Book1"));
    }

    @Transactional
    public void innerRequiresNewTransactionHasSuppressedException() {
        try {
            transactionRequiresNewFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void addBookRequiresNew() {
        bookRepository.save(newBook("Book1"));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void addBookRequiresNewFailing() {
        bookRepository.save(newBook("Book2"));
        throw new IllegalStateException("Big fail!");
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void transactionRequiresNewFailing() {
        throw new IllegalStateException("Big fail!");
    }

    @Transactional
    protected void transactionFailing() {
        throw new IllegalStateException("Big fail!");
    }

    @Transactional
    protected void transactionMarkedForRollback(Runnable markForRollback) {
        markForRollback.run();
    }

}
