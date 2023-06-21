package io.micronaut.transaction.hibernate6.micronaut;

import io.micronaut.data.tck.entities.Book;
import io.micronaut.transaction.annotation.ReadOnly;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class ReadOnlyTest {

    private final HibernateBookRepository bookRepository;

    public ReadOnlyTest(HibernateBookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @jakarta.transaction.Transactional
    public Long createBook() {
        Book book = new Book();
        book.setTitle("New book");
        book.setTotalPages(123);
        bookRepository.save(book);
        return book.getId();
    }

    @jakarta.transaction.Transactional
    public Book findBook(Long bookId) {
        return bookRepository.findById(bookId).get();
    }

    @ReadOnly
    public void bookIsNotUpdated1(Long bookId) {
        Book book = bookRepository.findById(bookId).get();
        book.setTitle("Xyz");
    }

    @Transactional(readOnly = true)
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

    @jakarta.transaction.Transactional
    public void bookIsUpdated(Long bookId) {
        Book book = bookRepository.findById(bookId).get();
        book.setTitle("Xyz");
    }
}
