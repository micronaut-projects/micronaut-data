package example;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// tag::listener[]
@Singleton
class BookCatalog {
    private final BookRepository repository;
    private final Map<Long, Book> books = new HashMap<>();

    BookCatalog(BookRepository repository) {
        this.repository = repository;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    synchronized void loadInitialState(StartupEvent event) {
        refresh();
    }

    // tag::events[]
    @ChangeListener
    @PostgresChangeNotification(channel = "book_changes")
    synchronized void changed(ChangeEvent<Book> event) {
        switch (event.operation()) {
            case INSERT -> event.entity().ifPresent(book -> books.put(book.id(), book));
            case UPDATE -> {
                PostgresChangeEventMetadata metadata = event.metadata(PostgresChangeEventMetadata.class).orElseThrow();
                // An UPDATE can change the primary key.
                books.remove(Long.valueOf(metadata.oldKeys().get("id")));
                event.entity().ifPresent(book -> books.put(book.id(), book));
            }
            case DELETE -> {
                PostgresChangeEventMetadata metadata = event.metadata(PostgresChangeEventMetadata.class).orElseThrow();
                books.remove(Long.valueOf(metadata.oldKeys().get("id")));
            }
            case INVALIDATE -> refresh();
        }
    }

    // end::events[]

    synchronized Optional<Book> find(Long id) {
        return Optional.ofNullable(books.get(id));
    }

    private void refresh() {
        books.clear();
        repository.findAll().forEach(book -> books.put(book.id(), book));
    }
}
// end::listener[]
