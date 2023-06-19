package io.micronaut.data.document.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.document.tck.entities.Book;
import io.micronaut.data.document.tck.repositories.BookRepository;

import jakarta.transaction.Transactional;

public abstract class AbstractBookService {

    public static final String BOOK_REPOSITORY_CLASS_PROPERTY = "bookRepositoryClass";

    protected final BookRepository bookRepository;

    public AbstractBookService(ApplicationContext beanContext) {
        Class<? extends BookRepository> bookRepositoryClass = (Class<? extends BookRepository>) ClassUtils.forName(
            beanContext.getProperty(BOOK_REPOSITORY_CLASS_PROPERTY, String.class).orElseThrow(),
            beanContext.getClassLoader()
        ).orElseThrow();
        this.bookRepository = beanContext.getBean(bookRepositoryClass);
    }

    @Transactional
    public long countBooksTransactional() {
        return bookRepository.count();
    }

    public long countBooks() {
        return bookRepository.count();
    }

    public void cleanup() {
        bookRepository.deleteAll();
    }

    protected Book newBook(String bookName) {
        Book book = new Book();
        book.setTitle(bookName);
        book.setTotalPages(1000);
        return book;
    }

}
