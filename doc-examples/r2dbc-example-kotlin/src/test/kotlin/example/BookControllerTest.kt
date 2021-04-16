package example

import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.r2dbc.spi.Connection
import io.reactivex.Flowable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MySQLContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookControllerTest : TestPropertyProvider {
    @Inject
    lateinit var bookClient: BookClient
    var container: MySQLContainer<*>? = null

    @BeforeAll
    fun setupData(operations: R2dbcOperations, authorRepository: AuthorRepository, bookRepository: BookRepository) {
        // tag::programmatic-tx[]
        Mono.from(operations.withTransaction {
            Flux.from(authorRepository.save(Author("Stephen King")))
                    .flatMap { author: Author ->
                        bookRepository.saveAll(listOf(
                                Book("The Stand", 1000, author),
                                Book("The Shining", 400, author)
                        ))
                    }
                    .thenMany(Flux.from(authorRepository.save(Author("James Patterson"))))
                    .flatMap { author: Author -> bookRepository.save(Book("Along Came a Spider", 300, author)) }.then()
        }).block()
        // end::programmatic-tx[]

        // tag::programmatic-tx-status[]
        Flowable.fromPublisher(operations.withTransaction { status: ReactiveTransactionStatus<Connection> ->  // <1>
            Flowable.fromPublisher(authorRepository.save(Author("Michael Crichton")))
                    .flatMap { author: Author ->
                        operations.withTransaction(status) {   // <2>
                            bookRepository.saveAll(listOf(
                                    Book("Jurassic Park", 300, author),
                                    Book("Disclosure", 400, author)
                            ))
                        }
                    }
        }).blockingSubscribe()
        // end::programmatic-tx-status[]
    }

    @AfterAll
    fun cleanup() {
        if (container != null) {
            container!!.stop()
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

    @Test
    fun testListBooksMicronautData() {
        val list = bookClient.list()
        assertEquals(
                5,
                list.size
        )
    }

    override fun getProperties(): Map<String, String> {
        container = MySqlServer.start()
        return mapOf(
                "datasources.default.url" to container!!.jdbcUrl,
                "datasources.default.username" to container!!.username,
                "datasources.default.password" to container!!.password,
                "datasources.default.database" to container!!.databaseName,
                "r2dbc.datasources.default.host" to container!!.host,
                "r2dbc.datasources.default.port" to container!!.firstMappedPort.toString(),
                "r2dbc.datasources.default.driver" to "mysql",
                "r2dbc.datasources.default.username" to container!!.username,
                "r2dbc.datasources.default.password" to container!!.password,
                "r2dbc.datasources.default.database" to container!!.databaseName
        )
    }

    @Client("/books")
    interface BookClient {
        @Get("/")
        fun list(): List<Book>
    }

}