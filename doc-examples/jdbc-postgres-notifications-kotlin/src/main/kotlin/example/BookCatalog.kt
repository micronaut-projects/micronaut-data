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
import java.util.Optional

// tag::listener[]
@Singleton
open class BookCatalog(private val repository: BookRepository) {
    private val books = HashMap<Long, Book>()

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Synchronized
    open fun loadInitialState(event: StartupEvent) {
        refresh()
    }

    // tag::events[]
    // tag::listener-signature[]
    @ChangeListener
    // end::listener-signature[]
    @PostgresChangeNotification(channel = "book_changes")
    @Synchronized
    // tag::listener-signature[]
    open fun changed(event: ChangeEvent<Book>) {
        // Handle the change.
    // end::listener-signature[]
        when (event.operation()) {
            ChangeOperation.INSERT -> event.entity().ifPresent { book -> books[book.id] = book }
            ChangeOperation.UPDATE -> {
                val metadata = event.metadata(PostgresChangeEventMetadata::class.java).orElseThrow()
                // An UPDATE can change the primary key.
                books.remove(metadata.oldKeys().getValue("id").toLong())
                event.entity().ifPresent { book -> books[book.id] = book }
            }
            ChangeOperation.DELETE -> {
                val metadata = event.metadata(PostgresChangeEventMetadata::class.java).orElseThrow()
                books.remove(metadata.oldKeys().getValue("id").toLong())
            }
            ChangeOperation.INVALIDATE -> refresh()
        }
        // tag::listener-signature[]
    }
    // end::listener-signature[]

    // end::events[]

    @Synchronized
    fun find(id: Long): Optional<Book> = Optional.ofNullable(books[id])

    private fun refresh() {
        books.clear()
        repository.findAll().forEach { book -> books[book.id] = book }
    }
}
// end::listener[]
