
package example;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import jakarta.inject.Singleton;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class BookManager {
    private final BookRepository bookRepository;
    private final ApplicationEventPublisher<NewBookEvent> eventPublisher;
    private final List<NewBookEvent> events = new ArrayList<>();

    public BookManager(BookRepository bookRepository, ApplicationEventPublisher<NewBookEvent> eventPublisher) {
        this.bookRepository = bookRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    void saveBook(String title, int pages) {
        final Book book = new Book(title, pages);
        bookRepository.save(book);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("No active Spring transaction!");
        }
        eventPublisher.publishEvent(new NewBookEvent(book));
    }

    @TransactionalEventListener
    void onNewBook(NewBookEvent event) {
        events.add(event);
    }

    public List<NewBookEvent> getEvents() {
        return events;
    }

    record NewBookEvent(Book book) {
    }
}
