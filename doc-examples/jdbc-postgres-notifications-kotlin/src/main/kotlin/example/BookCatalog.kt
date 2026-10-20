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

    @ChangeListener
    @PostgresChangeNotification(channel = "book_changes")
    @Synchronized
    open fun changed(event: ChangeEvent<Book>) {
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
    }

    @Synchronized
    fun find(id: Long): Optional<Book> = Optional.ofNullable(books[id])

    private fun refresh() {
        books.clear()
        repository.findAll().forEach { book -> books[book.id] = book }
    }
}
// end::listener[]
