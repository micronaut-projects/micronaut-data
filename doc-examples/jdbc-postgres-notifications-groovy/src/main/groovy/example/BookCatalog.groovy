package example

import io.micronaut.context.event.StartupEvent
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton

// tag::listener[]
@Singleton
class BookCatalog {
    private final BookRepository repository
    private final Map<Long, Book> books = new HashMap<>()

    BookCatalog(BookRepository repository) {
        this.repository = repository
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    synchronized void loadInitialState(StartupEvent event) {
        refresh()
    }

    // tag::events[]
    @ChangeListener
    @PostgresChangeNotification(channel = "book_changes")
    synchronized void changed(ChangeEvent<Book> event) {
        switch (event.operation()) {
            case ChangeOperation.INSERT:
                event.entity().ifPresent { book -> books.put(book.id(), book) }
                break
            case ChangeOperation.UPDATE:
                def metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
                // An UPDATE can change the primary key.
                books.remove(Long.valueOf(metadata.oldKeys().get("id")))
                event.entity().ifPresent { book -> books.put(book.id(), book) }
                break
            case ChangeOperation.DELETE:
                def metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
                books.remove(Long.valueOf(metadata.oldKeys().get("id")))
                break
            case ChangeOperation.INVALIDATE:
                refresh()
                break
        }
    }

    // end::events[]

    synchronized Optional<Book> find(Long id) {
        Optional.ofNullable(books.get(id))
    }

    private void refresh() {
        books.clear()
        repository.findAll().each { book -> books.put(book.id(), book) }
    }
}
// end::listener[]
