package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import jakarta.inject.Singleton;

import java.util.Optional;

@Requires(property = AbstractBookService.BOOK_REPOSITORY_CLASS_PROPERTY)
@Singleton
public class TxBookService extends AbstractBookService {

    public SynchronousTransactionManager<Object> transactionManager;
    public ConnectionOperations<Object> connectionOperations;

    public TxBookService(ApplicationContext beanContext) {
        super(beanContext);
    }

    public void bookAddedInConnectableNestedTransaction() {
        connectionOperations.executeWrite(connectionStatus -> {
            ConnectionStatus<Object> outerConnection = getConnection();
            TransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.Propagation.NESTED);
            TransactionStatus<Object> status = transactionManager.getTransaction(definition);
            ConnectionStatus<Object> txConnection = getConnection();
            if (!txConnection.equals(outerConnection)) {
                throw new IllegalStateException("Connection is not the same as the outer connection");
            }
            bookRepository.save(newBook("MandatoryBook"));
            transactionManager.commit(status);
            ConnectionStatus<Object> afterTxConnection = getConnection();
            if (!afterTxConnection.equals(outerConnection)) {
                throw new IllegalStateException("Connection is not the same as the outer connection");
            }
            return null;
        });
    }

    private ConnectionStatus<Object> getConnection() {
        Optional<ConnectionStatus<Object>> optionalConnection = connectionOperations.findConnectionStatus();
        if (optionalConnection.isEmpty()) {
            throw new IllegalStateException("No connection status available");
        }
        return optionalConnection.get();
    }

    @Transactional(name = "MyTx")
    public void bookAddedCustomNamedTransaction(Runnable checkTx) {
        bookRepository.save(newBook("MandatoryBook"));
        checkTx.run();
    }

    @Transactional(readOnly = true)
    public void bookAddedInReadOnlyTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Transactional(readOnly = true)
    public void readOnlyTxCallingAddingBookInAnotherTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    protected void addBook() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    public void bookAddedInMandatoryTransaction() {
        mandatoryTransaction();
    }

    public void bookAddedInMandatoryTransactionSync() {
        if (transactionManager.findTransactionStatus().isPresent()) {
            throw new IllegalStateException("No TX expected!");
        }
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        if (transactionManager.findTransactionStatus().isEmpty()) {
            throw new IllegalStateException("TX expected");
        }
        mandatoryTransaction();
        transactionManager.commit(transaction);
    }

    @Transactional(propagation = TransactionDefinition.Propagation.NESTED)
    public void bookAddedInNestedTransaction() {
        bookRepository.save(newBook("Book1"));
    }

    public void bookAddedInNestedTransactionSync() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.of(TransactionDefinition.Propagation.NESTED));
        bookRepository.save(newBook("Book1"));
        transactionManager.commit(transaction);
    }

    @Transactional(propagation = TransactionDefinition.Propagation.NESTED)
    public void bookAddedInAnotherNestedTransaction() {
        bookAddedInNestedTransaction();
    }

    public void bookAddedInAnotherNestedTransactionSync() {
        bookAddedInNestedTransactionSync();
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    public void bookAddedInNoSupportedPropagation(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
    public void bookAddedInNeverPropagation(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        // Spring TX manager will retain the connection and clean it up at the TX end
        noTxCheck.run();
    }

    public void bookAddedInNeverPropagationSync(Runnable noTxCheck) {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.of(TransactionDefinition.Propagation.NEVER));
        bookRepository.save(newBook("MandatoryBook"));
        // Spring TX manager will retain the connection and clean it up at the TX end
        noTxCheck.run();
        transactionManager.commit(transaction);
    }

    @jakarta.transaction.Transactional
    public void bookAddedInInnerNeverPropagation(Runnable noTxCheck) {
        bookAddedInNeverPropagation(noTxCheck);
    }

    public void bookAddedInInnerNeverPropagationSync(Runnable noTxCheck) {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        try {
            bookAddedInNeverPropagationSync(noTxCheck);
            transactionManager.commit(transaction);
        } catch (Exception e) {
            transactionManager.rollback(transaction);
            throw e;
        }
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    public void bookAddedInNoSupportedPropagationAndFailed(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
        throw new IllegalStateException("Big fail!");
    }

    @jakarta.transaction.Transactional
    public void bookAddedInInnerNoSupportedPropagation(Runnable noTxCheck) {
        bookAddedInNoSupportedPropagation(noTxCheck);
    }

    @jakarta.transaction.Transactional
    public void bookAddedInInnerNoSupportedPropagationFailedAndExceptionSuppressed(Runnable noTxCheck) {
        try {
            bookAddedInNoSupportedPropagationAndFailed(noTxCheck);
        } catch (Exception e) {
            // Ignore
        }
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.MANDATORY)
    public void mandatoryTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    public void mandatoryTransactionSync() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.of(TransactionDefinition.Propagation.MANDATORY));
        bookRepository.save(newBook("MandatoryBook"));
        transactionManager.commit(transaction);
    }

    @jakarta.transaction.Transactional
    public void checkInTransaction(Runnable runnable) {
        runnable.run();
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedInTxMethod() {
        bookRepository.save(newBook("Toys"));
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedInAnotherRequiresNewTx() {
        addBookRequiresNew();
    }

    public void bookIsAddedInAnotherRequiresNewTxSync() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        addBookRequiresNewSync();
        transactionManager.commit(transaction);
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedInAnotherRequiresNewTxWhichIsFailing() {
        addBookRequiresNewFailing();
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedAndAnotherRequiresNewTxIsFailing() {
        bookRepository.save(newBook("Book1"));
        transactionRequiresNewFailing();
    }

    public void bookIsAddedAndAnotherRequiresNewTxIsFailingSync() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        bookRepository.save(newBook("Book1"));
        try {
            transactionRequiresNewFailing();
            transactionManager.commit(transaction);
        } catch (Exception e) {
            transactionManager.rollback(transaction);
            throw e;
        }
    }

    @jakarta.transaction.Transactional
    public void innerTransactionHasSuppressedException() {
        try {
            transactionFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    public void innerTransactionHasSuppressedExceptionSync() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        transactionFailingSync();
        bookRepository.save(newBook("Book1"));
        transactionManager.commit(transaction);
    }

    public void innerTransactionHasSuppressedExceptionSync2() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        try {
            transactionFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
        transactionManager.commit(transaction);
    }

    @jakarta.transaction.Transactional
    public void innerTransactionMarkedForRollback(Runnable markForRollback) {
        transactionMarkedForRollback(markForRollback);
        bookRepository.save(newBook("Book1"));
    }

    @jakarta.transaction.Transactional
    public void saveAndMarkedForRollback(Runnable markForRollback) {
        bookRepository.save(newBook("Book1"));
        markForRollback.run();
    }

    @jakarta.transaction.Transactional
    public void saveAndMarkedForRollback2(Runnable markForRollback) {
        markForRollback.run();
        bookRepository.save(newBook("Book1"));
    }

    @jakarta.transaction.Transactional
    public void innerRequiresNewTransactionHasSuppressedException() {
        try {
            transactionRequiresNewFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected void addBookRequiresNew() {
        bookRepository.save(newBook("Book1"));
    }

    public void addBookRequiresNewSync() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.of(TransactionDefinition.Propagation.REQUIRES_NEW));
        bookRepository.save(newBook("Book1"));
        transactionManager.commit(transaction);
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected void addBookRequiresNewFailing() {
        bookRepository.save(newBook("Book2"));
        throw new IllegalStateException("Big fail!");
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected void transactionRequiresNewFailing() {
        throw new IllegalStateException("Big fail!");
    }

    @jakarta.transaction.Transactional
    protected void transactionFailing() {
        throw new IllegalStateException("Big fail!");
    }

    protected void transactionFailingSync() {
        TransactionStatus<Object> transaction = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        transactionManager.rollback(transaction);
    }

    @jakarta.transaction.Transactional
    protected void transactionMarkedForRollback(Runnable markForRollback) {
        markForRollback.run();
    }

}
