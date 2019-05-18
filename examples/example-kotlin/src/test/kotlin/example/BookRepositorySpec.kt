package example

import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MicronautTest(rollback = false)
class BookRepositorySpec {

    // tag::inject[]
    @Inject
    lateinit var bookRepository: BookRepository
    // end::inject[]

    @Test
    fun testCrud() {
        assertNotNull(bookRepository)

        // Create: Save a new book
        // tag::save[]
        var book = Book(0,"The Stand", 1000)
        bookRepository.save(book)
        // end::save[]

        val id = book.id
        assertNotNull(id)

        // Read: Read a book from the database
        // tag::read[]
        book = bookRepository.findById(id).orElse(null)
        // end::read[]
        assertNotNull(book)
        assertEquals("The Stand", book.title)

        // Check the count
        assertEquals(1, bookRepository.count())
        assertTrue(bookRepository.findAll().iterator().hasNext())

        // Update: Update the book and save it again
        // tag::update[]
        book.title = "Changed"
        bookRepository.save(book)
        // end::update[]
        book = bookRepository.findById(id).orElse(null)
        assertEquals("Changed", book.title)

        // Delete: Delete the book
        // tag::delete[]
        bookRepository.deleteById(id)
        // end::delete[]
        assertEquals(0, bookRepository.count())
    }
}