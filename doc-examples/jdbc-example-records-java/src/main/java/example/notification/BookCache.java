package example.notification;

import example.Book;
import example.BookRepository;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.data.jdbc.annotation.ChangeListener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Context
@Requires(property = "query-notification.object.enabled")
final class BookCache implements ApplicationEventListener<StartupEvent> {

    private final BookRepository repository;
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    BookCache(BookRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        repository.findAll().forEach(book -> books.put(book.id(), book));
    }

    public Optional<Book> find(String title) {
        return books.values()
            .stream()
            .filter(book -> book.title().equals(title))
            .findFirst();
    }

    @ChangeListener(properties = @ChangeListener.Property(
        name = "DCN_CLIENT_INIT_CONNECTION", value = "true"
    ))
    void onBookChanged(Book book) {
        books.put(book.id(), book);
    }
}
