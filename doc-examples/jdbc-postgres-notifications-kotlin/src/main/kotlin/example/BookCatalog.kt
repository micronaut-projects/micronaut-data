package example

import io.micronaut.context.event.StartupEvent
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import java.util.*

// tag::listener[]
@Singleton
open class BookCatalog(private val repository: BookRepository) {
    // end::listener[]
    private val books = HashMap<Long, Book>()
    private var cacheEnabled = true

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Synchronized
    open fun loadInitialState(event: StartupEvent) {
        refresh()
    }

    // tag::listener[]
    // tag::listener-signature[]
    @ChangeListener
    // end::listener-signature[]
    @PostgresChangeNotification(channel = "book_changes") // <1>
    // tag::events[]
    @Synchronized
    // tag::listener-signature[]
    open fun changed(event: ChangeEvent<Book>) {
        // Handle the change.
        // end::listener[]
    // end::listener-signature[]
        if (!cacheEnabled) {
            return
        }
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
            ChangeOperation.INVALIDATE -> refresh() // <1>
            ChangeOperation.TERMINATED -> {
                cacheEnabled = false // <2>
                books.clear()
            }
        }
        // tag::listener-signature[]
        // tag::listener[]
    }
    // end::listener[]
    // end::listener-signature[]

    // end::events[]

    @Synchronized
    fun find(id: Long): Optional<Book> = if (cacheEnabled) Optional.ofNullable(books[id]) else repository.findById(id)

    private fun refresh() {
        if (!cacheEnabled) {
            return
        }
        books.clear()
        repository.findAll().forEach { book -> books[book.id] = book }
    }
    // tag::listener[]
}
// end::listener[]
