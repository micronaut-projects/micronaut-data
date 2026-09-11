package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@MicronautTest(transactional = false)
class AuthorRepositoryTest {

    @Inject
    lateinit var authorRepository: AuthorRepository

    @AfterEach
    fun cleanup() {
        authorRepository.deleteAll()
    }

    @Test
    fun testCascadePersist() {
        val author = Author("Stephen King")
        val book1 = Book("The Stand")
        val book2 = Book("The Shining")
        book1.author = author
        book2.author = author
        author.books.add(book1)
        author.books.add(book2)

        authorRepository.save(author)
        assertNotNull(author.id)
        assertNotNull(book1.id)
        assertNotNull(book2.id)

        val saved = authorRepository.findById(author.id!!).orElse(null)
        assertNotNull(saved)
        assertEquals("Stephen King", saved!!.name)
        assertEquals(2, saved.books.size)
    }

    @Test
    fun testJoinFetch() {
        val author = Author("Stephen King")
        val book = Book("The Stand")
        book.author = author
        author.books.add(book)
        authorRepository.save(author)

        val authorWithBooks = authorRepository.findById(author.id!!).orElse(null)
        assertNotNull(authorWithBooks)
        assertEquals(1, authorWithBooks!!.books.size)
        assertEquals("The Stand", authorWithBooks.books.first().title)
    }

    @Test
    fun testSearchByNameWithJoin() {
        val author = Author("Stephen King")
        val book = Book("The Stand")
        book.author = author
        author.books.add(book)
        authorRepository.save(author)

        val found = authorRepository.searchByName("Stephen King")
        assertNotNull(found)
        assertEquals(1, found!!.books.size)
    }

    @Test
    fun testReverseLookupByBookTitle() {
        val author = Author("Stephen King")
        val book = Book("The Stand")
        book.author = author
        author.books.add(book)
        authorRepository.save(author)

        // Verify the author was saved with the book
        val found = authorRepository.findById(author.id!!).orElse(null)
        assertNotNull(found)
        assertEquals("Stephen King", found!!.name)
        assertEquals(1, found.books.size)
    }
}
