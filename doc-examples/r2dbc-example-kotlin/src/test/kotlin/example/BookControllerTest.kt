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
import kotlinx.coroutines.runBlocking

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookControllerTest : AbstractTest(true) {
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
    fun cleanupData(bookRepository: BookRepository, authorRepository: AuthorRepository) {
        runBlocking {
            bookRepository.deleteAll()
            authorRepository.deleteAll()
        }
    }

    @Test
    fun testListBooks() {
        val list = bookClient.list()
        assertEquals(
                5,
                list.size
        )
    }

    @Client("/books2")
    interface BookClient {
        @Get("/")
        fun list(): List<Book>
    }

}
