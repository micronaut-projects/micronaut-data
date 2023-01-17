
package example;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import jakarta.inject.Singleton;
import javax.transaction.Transactional;

@Singleton
public class BookManager {
    private final BookRepository bookRepository;
    private final ApplicationEventPublisher<NewBookEvent> eventPublisher;

    public BookManager(BookRepository bookRepository, ApplicationEventPublisher<NewBookEvent> eventPublisher) { // <1>
        this.bookRepository = bookRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    void saveBook(String title, int pages) {
        final Book book = new Book(title, pages);
        bookRepository.save(book);
        eventPublisher.publishEvent(new NewBookEvent(book)); // <2>
    }

    @TransactionalEventListener
    void onNewBook(NewBookEvent event) {
        System.out.println("book = " + event.book); // <3>
    }

    static class NewBookEvent {
        final Book book;

        public NewBookEvent(Book book) {
            this.book = book;
        }
    }
}
