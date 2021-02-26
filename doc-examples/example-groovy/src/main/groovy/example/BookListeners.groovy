package example

import io.micronaut.context.annotation.Factory
import io.micronaut.data.event.listeners.PostPersistEventListener
import io.micronaut.data.event.listeners.PrePersistEventListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.inject.Singleton

@Factory
class BookListeners {
    private static final Logger LOG = LoggerFactory.getLogger(BookListeners)

    @Singleton
    PrePersistEventListener<Book> beforeBookPersist() { // <1>
        return (book) -> {
            LOG.debug "Inserting book: ${book.title}"
            return true // <2>
        }
    }

    @Singleton
    PostPersistEventListener<Book> afterBookPersist() { // <3>
        return (book) -> LOG.debug("Book inserted: ${book.title}")
    }
}
