package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@MicronautTest(transactional = false)
class BookRepositorySpec {

    @Inject
    lateinit var bookRepository: BookRepository

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
    }

    @Test
    fun testCrud() {
        // Create
        val book = Book("The Stand")
        bookRepository.save(book)
        val id = book.id
        assertNotNull(id)

        // Read
        val found = bookRepository.findById(id!!).orElse(null)
        assertNotNull(found)
        assertEquals("The Stand", found!!.title)

        // Update
        bookRepository.update(id, "Changed")
        val updated = bookRepository.findById(id).orElse(null)
        assertNotNull(updated)
        assertEquals("Changed", updated!!.title)

        // Delete
        bookRepository.deleteById(id)
        assertEquals(0, bookRepository.count())
    }

    @Test
    fun testFindByTitle() {
        bookRepository.save(Book("The Stand"))
        assertTrue(bookRepository.findByTitle("The Stand").isPresent)
    }
}
