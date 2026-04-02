package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@MicronautTest(transactional = false)
class BookReactiveReturningRepositoryTest : AbstractTest(false) {

    @Inject
    lateinit var repository: BookReactiveReturningRepository

    @Inject
    lateinit var blockingAuthorRepository: BlockingAuthorRepository

    @Inject
    lateinit var blockingBookRepository: BlockingBookRepository

    @AfterEach
    fun cleanupData() {
        blockingBookRepository.deleteAll()
        blockingAuthorRepository.deleteAll()
    }

    @Test
    fun testReactiveReturningInsertUpdateDelete() = runBlocking {
        val author = blockingAuthorRepository.save(Author("Returning Author"))

        val saved = repository.saveReturning(Book("Returning Book", 300, author)).awaitSingle()
        assertNotNull(saved.id)
        assertEquals("Returning Book", saved.title)

        val savedId = requireNotNull(saved.id)
        val updated = repository.customUpdateReturning(301, listOf(savedId)).collectList().awaitSingle().single()
        assertEquals(savedId, updated.id)
        assertEquals(301, updated.pages)

        val deleted = repository.deleteReturning(updated).awaitSingle()
        assertEquals(savedId, deleted.id)
        assertEquals(301, deleted.pages)
    }
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface BookReactiveReturningRepository {
    fun saveReturning(book: Book): Mono<Book>

    fun deleteReturning(book: Book): Mono<Book>

    @Query("""
        UPDATE book SET pages = :pages WHERE id IN (:ids) RETURNING *
        """)
    fun customUpdateReturning(pages: Int, ids: Iterable<Long>): Flux<Book>
}
