package io.micronaut.data.spring.hibernate.micronaut;

import io.micronaut.data.tck.entities.Book;
import io.micronaut.transaction.annotation.ReadOnly;
import io.micronaut.transaction.annotation.TransactionalAdvice;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class ReadOnlyTest {

    private final HibernateBookRepository bookRepository;

    public ReadOnlyTest(HibernateBookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional
    public Long createBook() {
        Book book = new Book();
        book.setTitle("New book");
        book.setTotalPages(123);
        bookRepository.save(book);
        return book.getId();
    }

    @Transactional
    public Book findBook(Long bookId) {
        return bookRepository.findById(bookId).get();
    }

    @ReadOnly
    public void bookIsNotUpdated1(Long bookId) {
        Book book = bookRepository.findById(bookId).get();
        book.setTitle("Xyz");
    }

    @TransactionalAdvice(readOnly = true)
    public void bookIsNotUpdated2(Long bookId) {
        Book book = bookRepository.findById(bookId).get();
        book.setTitle("Xyz");
    }

    // TODO: Fix this combination
//    @Transactional
    @ReadOnly
    public void bookIsNotUpdated3(Long bookId) {
        Book book = bookRepository.findById(bookId).get();
        book.setTitle("Xyz");
    }

    @ReadOnly
    // TODO: Fix this combination
//    @Transactional
    public void bookIsNotUpdated4(Long bookId) {
        Book book = bookRepository.findById(bookId).get();
        book.setTitle("Xyz");
    }

    @Transactional
    public void bookIsUpdated(Long bookId) {
        Book book = bookRepository.findById(bookId).get();
        book.setTitle("Xyz");
    }
}
