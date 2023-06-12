package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;
import io.micronaut.data.tck.repositories.SimpleBookRepository;
import io.micronaut.data.tck.repositories.SimpleReactiveBookRepository;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import jakarta.transaction.Transactional;
import reactor.core.publisher.Mono;

import java.util.Optional;

public abstract class AbstractReactiveBookService {

    public static final String BOOK_REPOSITORY_CLASS_PROPERTY = "reactiveBookRepositoryClass";

    protected final SimpleReactiveBookRepository bookRepository;
    protected final ReactorReactiveTransactionOperations<?> transactionOperations;

    public AbstractReactiveBookService(ApplicationContext beanContext) {
        Class<? extends SimpleReactiveBookRepository> bookRepositoryClass = (Class<? extends SimpleReactiveBookRepository>) ClassUtils.forName(
            beanContext.getProperty(BOOK_REPOSITORY_CLASS_PROPERTY, String.class).orElseThrow(),
            beanContext.getClassLoader()
        ).orElseThrow();
        this.bookRepository = beanContext.getBean(bookRepositoryClass);
        this.transactionOperations = beanContext.getBean(ReactorReactiveTransactionOperations.class);
    }

    public SimpleReactiveBookRepository getBookRepository() {
        return bookRepository;
    }

    public ReactorReactiveTransactionOperations<?> getTransactionOperations() {
        return transactionOperations;
    }

    public Mono<Long> cleanup() {
        return Mono.from(bookRepository.deleteAll());
    }

    @Transactional
    public Mono<Long> countBooksTransactional() {
        return Mono.from(bookRepository.count());
    }

    public Mono<Long> countBooks() {
        return Mono.from(bookRepository.count());
    }

    public Mono<Book> save(Book book) {
        return Mono.from(bookRepository.save(book));
    }

    protected Book newBook(String bookName) {
        Book book = new Book();
        book.setTitle(bookName);
        book.setTotalPages(1000);
        return book;
    }

    protected <T> Mono<T> checkNoTxIsPresent(T item) {
        return Mono.deferContextual(contextView -> {
            Optional<? extends ReactiveTransactionStatus<?>> transactionStatus = transactionOperations.findTransactionStatus(contextView);
            if (transactionStatus.isPresent()) {
                return Mono.error(new IllegalStateException("TX shouldn't be present"));
            }
            return Mono.just(item);
        });
    }

    protected <T> Mono<T> checkTxIsPresent(T item) {
        return Mono.deferContextual(contextView -> {
            Optional<? extends ReactiveTransactionStatus<?>> transactionStatus = transactionOperations.findTransactionStatus(contextView);
            if (transactionStatus.isEmpty()) {
                return Mono.error(new IllegalStateException("TX should be present"));
            }
            return Mono.just(item);
        });
    }

}
