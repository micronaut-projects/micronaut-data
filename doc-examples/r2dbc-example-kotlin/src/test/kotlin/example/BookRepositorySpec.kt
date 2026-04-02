package example

import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.query
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositorySpec : AbstractTest(false) {

    @Inject
    lateinit var blockingBookRepository: BlockingBookRepository

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var blockingAuthorRepository: BlockingAuthorRepository

    @AfterEach
    fun cleanupData() {
        blockingBookRepository.deleteAll()
        blockingAuthorRepository.deleteAll()
    }

    @Test
    fun testDto() {
        runBlocking {
            val author = Author("Some")
            blockingAuthorRepository.save(author)
            blockingBookRepository.save(Book("The Shining", 400, author))
            val bookDTO = bookRepository.customFindOne("The Shining")!!
            assertEquals("The Shining", bookDTO.title)
            val bookDTO2 = bookRepository.findOne("The Shining")!!
            assertEquals("The Shining", bookDTO2.title)
        }
    }
    
    @Test
    fun testMultipleDtoQuery() {
        runBlocking {
            val author = Author("Some")
            blockingAuthorRepository.save(author)
            blockingBookRepository.save(Book("The Shining", 400, author))
            blockingBookRepository.save(Book("Leviathan Wakes", 600, author))
            val bookDTOs = bookRepository.findAll(query<Book, BookDTO> {
                multiselect(
                    Book::title,
                    Book::pages
                )
                where {
                    root[Book::pages] greaterThan 300
                }

            })
            assertEquals(bookDTOs.count(), 2)
        }
    }

    @Test
    fun testSuspendReturningInsertUpdateDelete() {
        runBlocking {
            val author = blockingAuthorRepository.save(Author("Returning Author"))

            val saved = bookRepository.saveReturning(Book("Returning Book", 300, author))
            val savedId = requireNotNull(saved.id)
            assertEquals("Returning Book", saved.title)
            assertEquals(300, saved.pages)

            val updated = bookRepository.updateReturning(savedId, "Returning Book Updated", 301)
            assertEquals(savedId, updated.id)
            assertEquals("Returning Book Updated", updated.title)
            assertEquals(301, updated.pages)

            val deleted = bookRepository.deleteReturning(savedId)
            assertEquals(savedId, deleted.id)
            assertEquals("Returning Book Updated", deleted.title)
            assertEquals(301, deleted.pages)
        }
    }

    @Test
    fun testSuspendReturningInsertUpdateDeleteMany() {
        runBlocking {
            val author = blockingAuthorRepository.save(Author("Returning Many Author"))

            val saved = bookRepository.saveReturningMany(
                listOf(
                    Book("Returning Many One", 200, author),
                    Book("Returning Many Two", 201, author)
                )
            ).toList()

            assertEquals(2, saved.size)
            val savedIds = saved.map { requireNotNull(it.id) }
            assertEquals(listOf("Returning Many One", "Returning Many Two"), saved.map { it.title })

            val updated = bookRepository.updateReturningMany(savedIds, 250).toList()
            assertEquals(savedIds.toSet(), updated.map { requireNotNull(it.id) }.toSet())
            assertEquals(listOf(250, 250), updated.map { it.pages })

            val deleted = bookRepository.deleteReturningMany(savedIds).toList()
            assertEquals(savedIds.toSet(), deleted.map { requireNotNull(it.id) }.toSet())
            assertEquals(listOf(250, 250), deleted.map { it.pages })
        }
    }

    @Test
    fun testSuspendReturningInsertUpdateDeleteManyAsList() {
        runBlocking {
            val author = blockingAuthorRepository.save(Author("Returning Suspend Many Author"))

            val saved = bookRepository.saveReturningManyAsList(
                listOf(
                    Book("Returning Suspend Many One", 220, author),
                    Book("Returning Suspend Many Two", 221, author)
                )
            )

            assertEquals(2, saved.size)
            val savedIds = saved.map { requireNotNull(it.id) }
            assertEquals(listOf("Returning Suspend Many One", "Returning Suspend Many Two"), saved.map { it.title })

            val updated = bookRepository.updateReturningManyAsList(savedIds, 260)
            assertEquals(savedIds.toSet(), updated.map { requireNotNull(it.id) }.toSet())
            assertEquals(listOf(260, 260), updated.map { it.pages })

            val deleted = bookRepository.deleteReturningManyAsList(savedIds)
            assertEquals(savedIds.toSet(), deleted.map { requireNotNull(it.id) }.toSet())
            assertEquals(listOf(260, 260), deleted.map { it.pages })
        }
    }
}
