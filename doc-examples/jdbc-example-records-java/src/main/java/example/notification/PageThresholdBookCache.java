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
@Requires(property = "query-notification.query.enabled")
final class PageThresholdBookCache implements ApplicationEventListener<StartupEvent> {

    private final BookRepository repository;
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    PageThresholdBookCache(BookRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        repository.findByPagesGreaterThanEquals(200).forEach(book -> books.put(book.id(), book));
    }

    public Optional<Book> find(String title) {
        return books.values()
            .stream()
            .filter(book -> book.title().equals(title))
            .findFirst();
    }

    @ChangeListener(
        select = "title",
        where = "pages >= 200",
        properties = {
            @ChangeListener.Property(name = "DCN_CLIENT_INIT_CONNECTION", value = "true"),
            @ChangeListener.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
        }
    )
    void onBookChanged(Book book) {
        if (book.pages() >= 200) {
            books.put(book.id(), book);
        } else {
            books.remove(book.id());
        }
    }
}
