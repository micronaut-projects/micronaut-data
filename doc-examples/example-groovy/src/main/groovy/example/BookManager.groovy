package example

import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.transaction.annotation.TransactionalEventListener
import javax.inject.Singleton
import javax.transaction.Transactional

@Singleton
class BookManager {
    private final BookRepository bookRepository
    private final ApplicationEventPublisher eventPublisher

    BookManager(BookRepository bookRepository, ApplicationEventPublisher eventPublisher) { // <1>
        this.bookRepository = bookRepository
        this.eventPublisher = eventPublisher
    }

    @Transactional
    void saveBook(String title, int pages) {
        final Book book = new Book(title, pages)
        bookRepository.save(book)
        eventPublisher.publishEvent(new NewBookEvent(book)) // <2>
    }

    @TransactionalEventListener
    void onNewBook(NewBookEvent event) {
        println("book = $event.book") // <3>
    }

    static class NewBookEvent {
        final Book book

        NewBookEvent(Book book) {
            this.book = book
        }
    }
}
