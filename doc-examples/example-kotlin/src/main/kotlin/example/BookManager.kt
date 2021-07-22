
package example

import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Singleton
import javax.transaction.Transactional

@Singleton
open class BookManager(
        private val bookRepository: BookRepository, private val eventPublisher: ApplicationEventPublisher<NewBookEvent>) { // <1>

    @Transactional
    open fun saveBook(title: String, pages: Int) {
        val book = Book(0, title, pages)
        bookRepository.save(book)
        eventPublisher.publishEvent(NewBookEvent(book)) // <2>
    }

    @TransactionalEventListener
    open fun onNewBook(event: NewBookEvent) {
        println("book = ${event.book}") // <3>
    }

    class NewBookEvent(val book: Book)
}
