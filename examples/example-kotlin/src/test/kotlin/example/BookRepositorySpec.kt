package example

import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MicronautTest(rollback = false)
class BookRepositorySpec {

    @Inject
    lateinit var bookRepository: BookRepository

    @Test
    fun testCrud() {
        assertNotNull(bookRepository)

        // Create: Save a new book
        var book = Book(0,"The Stand")
        book.title = "The Stand"
        book.pages = 1000
        bookRepository.save(book)

        val id = book.id
        assertNotNull(id)

        // Read: Read a book from the database
        book = bookRepository.findById(id).orElse(null)
        assertNotNull(book)
        assertEquals("The Stand", book.title)

        // Check the count
        assertEquals(1, bookRepository.count())
        assertTrue(bookRepository.findAll().iterator().hasNext())

        // Update: Update the book and save it again
        book.title = "Changed"
        bookRepository.save(book)
        book = bookRepository.findById(id).orElse(null)
        assertEquals("Changed", book.title)

        // Delete: Delete the book
        bookRepository.deleteById(id)
        assertEquals(0, bookRepository.count())
    }
}