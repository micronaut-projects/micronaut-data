package example

import io.micronaut.context.annotation.Factory
import io.micronaut.data.event.listeners.PostPersistEventListener
import io.micronaut.data.event.listeners.PrePersistEventListener
import org.slf4j.LoggerFactory
import javax.inject.Singleton

@Factory
class BookListeners {
    @Singleton
    fun beforeBookPersist(): PrePersistEventListener<Book> { // <1>
        return PrePersistEventListener { book: Book ->
            LOG.debug("Inserting book: ${book.title}")
            true // <2>
        }
    }

    @Singleton
    fun afterBookPersist(): PostPersistEventListener<Book> { // <3>
        return PostPersistEventListener { book: Book ->
            LOG.debug("Book inserted: ${book.title}")
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(BookListeners::class.java)
    }
}