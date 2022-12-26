package example

import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

@Retry
@MicronautTest(transactional = false)
class BookControllerTest extends Specification {

    @Inject BookClient bookClient

    @Shared @Inject R2dbcOperations operations
    @Shared @Inject AuthorRepository authorRepository
    @Shared @Inject BookRepository bookRepository

    def setupSpec() {
        // tag::programmatic-tx[]
        Flux.from(operations.withTransaction(status ->
                Flux.from(authorRepository.save(new Author("Stephen King")))
                        .flatMap((author -> bookRepository.saveAll([
                                new Book("The Stand", 1000, author),
                                new Book("The Shining", 400, author)
                        ])))
                        .thenMany(Flux.from(authorRepository.save(new Author("James Patterson"))))
                        .flatMap((author ->
                                bookRepository.save(new Book("Along Came a Spider", 300, author))
                        )).then()
        )).collectList().block()
        // end::programmatic-tx[]

        // tag::programmatic-tx-status[]
        Flux.from(operations.withTransaction(status -> // <1>
                Flux.from(authorRepository.save(new Author("Michael Crichton")))
                        .flatMap((author -> operations.withTransaction(status, (s) -> // <2>
                                bookRepository.saveAll([
                                        new Book("Jurassic Park", 300, author),
                                        new Book("Disclosure", 400, author)
                                ]))))
        )).collectList().block()
        // end::programmatic-tx-status[]
    }

    void "test list books"() {
        when:
        List<Book> list = bookClient.list()

        then:
        list.size() == 5
    }

    @Client("/books")
    static interface BookClient {
        @Get("/")
        List<Book> list()
    }
}
