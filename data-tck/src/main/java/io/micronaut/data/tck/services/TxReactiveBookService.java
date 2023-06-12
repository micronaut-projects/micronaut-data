package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Requires(property = AbstractReactiveBookService.BOOK_REPOSITORY_CLASS_PROPERTY)
@Singleton
public class TxReactiveBookService extends AbstractReactiveBookService {

    public TxReactiveBookService(ApplicationContext beanContext) {
        super(beanContext);
    }

    @Transactional(readOnly = true)
    public Mono<? extends Book> bookAddedInReadOnlyTransaction() {
        return save(newBook("MandatoryBook"));
    }

    @Transactional(readOnly = true)
    public Mono<? extends Book> readOnlyTxCallingAddingBookInAnotherTransaction() {
        return save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    protected Mono<? extends Book> addBook() {
        return save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    public Mono<? extends Book> bookAddedInMandatoryTransaction() {
        return mandatoryTransaction();
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    public Mono<Book> bookAddedInNoSupportedPropagation() {
        return save(newBook("MandatoryBook")).flatMap(this::checkNoTxIsPresent);
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
    public Mono<Book> bookAddedInNeverPropagation() {
        return save(newBook("MandatoryBook")).flatMap(this::checkNoTxIsPresent);
    }

    @jakarta.transaction.Transactional
    public Mono<Book> bookAddedInInnerNeverPropagation() {
        return bookAddedInNeverPropagation().flatMap(this::checkNoTxIsPresent);
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    public Mono<Book> bookAddedInNoSupportedPropagationAndFailed() {
        return save(newBook("MandatoryBook"))
            .flatMap(this::checkNoTxIsPresent)
            .then(Mono.error(() -> new IllegalStateException("Big fail!")));
    }

    @jakarta.transaction.Transactional
    public Mono<Book> bookAddedInInnerNoSupportedPropagation() {
        return bookAddedInNoSupportedPropagation()
            .flatMap(this::checkNoTxIsPresent);
    }

    @jakarta.transaction.Transactional
    public Mono<Void> bookAddedInInnerNoSupportedPropagationFailedAndExceptionSuppressed() {
        return bookAddedInNoSupportedPropagationAndFailed().then().onErrorResume(throwable -> Mono.empty());
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.MANDATORY)
    public Mono<? extends Book> mandatoryTransaction() {
        return save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    public Mono<Void> checkInTransaction() {
        return checkTxIsPresent("nothing").then();
    }

    @jakarta.transaction.Transactional
    public Mono<? extends Book> bookIsAddedInTxMethod() {
        return save(newBook("Toys"));
    }

    @jakarta.transaction.Transactional
    public Mono<? extends Book> bookIsAddedInAnotherRequiresNewTx() {
        return addBookRequiresNew();
    }

    @jakarta.transaction.Transactional
    public Mono<Book> bookIsAddedInAnotherRequiresNewTxWhichIsFailing() {
        return addBookRequiresNewFailing();
    }

    @jakarta.transaction.Transactional
    public Mono<Book> bookIsAddedAndAnotherRequiresNewTxIsFailing() {
        return save(newBook("Book1")).flatMap(book -> transactionRequiresNewFailing());
    }

    @jakarta.transaction.Transactional
    public Mono<Book> innerTransactionHasSuppressedException() {
        return this.<Book>transactionFailing().onErrorResume(throwable -> save(newBook("Book1")));
    }

    @jakarta.transaction.Transactional
    public Mono<? extends Book> innerTransactionMarkedForRollback() {
        return markForRollback().then(save(newBook("Book1")));
    }

    @jakarta.transaction.Transactional
    public Mono<Book> saveAndMarkedForRollback() {
        return save(newBook("Book1")).flatMap(ignore -> markForRollback().thenReturn(ignore));
    }

    @jakarta.transaction.Transactional
    public Mono<? extends Book> saveAndMarkedForRollback2() {
        return markForRollback().then(save(newBook("Book1")));
    }

    @jakarta.transaction.Transactional
    public Mono<Book> innerRequiresNewTransactionHasSuppressedException() {
        return this.<Book>transactionRequiresNewFailing().onErrorResume(throwable -> save(newBook("Book1")));
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected Mono<? extends Book> addBookRequiresNew() {
        return save(newBook("Book1"));
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected Mono<Book> addBookRequiresNewFailing() {
        return save(newBook("Book2")).flatMap(book -> Mono.error(new IllegalStateException("Big fail!")));
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected <T> Mono<T> transactionRequiresNewFailing() {
        return Mono.error(new IllegalStateException("Big fail!"));
    }

    @jakarta.transaction.Transactional
    protected <T> Mono<T> transactionFailing() {
        return Mono.error(new IllegalStateException("Big fail!"));
    }

    @jakarta.transaction.Transactional
    protected <T> Mono<T> transactionMarkedForRollback() {
        return markForRollback();
    }

    private <T> Mono<T> markForRollback() {
        return Mono.deferContextual(contextView -> {
            transactionOperations.findTransactionStatus(contextView).orElseThrow().setRollbackOnly();
            return Mono.empty();
        });
    }

}
