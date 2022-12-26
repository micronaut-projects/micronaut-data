package example

import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.r2dbc.spi.Connection
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookCoroutinesControllerTest : AbstractTest(false) {
    @Inject
    lateinit var bookClient: BookClient

    @BeforeAll
    fun setupData(operations: R2dbcOperations, authorRepository: AuthorRepository, bookRepository: BookReactiveRepository) {
        // tag::programmatic-tx[]
        Flux.from(operations.withTransaction {
            Flux.from(authorRepository.save(Author("Stephen King")))
                    .flatMap { author: Author ->
                        bookRepository.saveAll(listOf(
                                Book("The Stand", 1000, author),
                                Book("The Shining", 400, author)
                        ))
                    }
                    .thenMany(Flux.from(authorRepository.save(Author("James Patterson"))))
                    .flatMap { author: Author -> bookRepository.save(Book("Along Came a Spider", 300, author)) }.then()
        }).collectList().block()
        // end::programmatic-tx[]

        // tag::programmatic-tx-status[]
        Flux.from(operations.withTransaction { status: ReactiveTransactionStatus<Connection> ->  // <1>
            Flux.from(authorRepository.save(Author("Michael Crichton")))
                    .flatMap { author: Author ->
                        operations.withTransaction(status) {   // <2>
                            bookRepository.saveAll(listOf(
                                    Book("Jurassic Park", 300, author),
                                    Book("Disclosure", 400, author)
                            ))
                        }
                    }
        }).collectList().block()
        // end::programmatic-tx-status[]
    }

    @AfterAll
    fun cleanupData(bookRepository: BookReactiveRepository) {
        bookRepository.deleteAll()
    }

    @Test
    fun testListBooks() = runBlocking {
        val list = bookClient.list().toList()
        assertEquals(
                5,
                list.size
        )
    }

    @Test
    fun testFindBook() = runBlocking {
        val list = bookClient.list().toList()
        val firstBook = list[0]
        val book = bookClient.show(firstBook.id!!)!!
        assertEquals(
                firstBook.title,
                book.title
        )
    }

    @Test
    fun testMissingBook() = runBlocking {
        val book = bookClient.show(1111111)
        assertTrue(book == null)
    }

    @Client("/books")
    interface BookClient {

        @Get("/{id}")
        suspend fun show(id: Long): Book?

        @Get("/")
        fun list(): Flow<Book>
    }

}
