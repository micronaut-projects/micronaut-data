package example

import io.micronaut.core.util.CollectionUtils
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.testcontainers.containers.PostgreSQLContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

@Retry
@MicronautTest(transactional = false)
class BookControllerTest extends Specification implements TestPropertyProvider {

    static container

    @Inject BookClient bookClient

    @Shared @Inject R2dbcOperations operations
    @Shared @Inject AuthorRepository authorRepository
    @Shared @Inject BookRepository bookRepository

    def setupSpec() {
        // tag::programmatic-tx[]
        Mono.fromDirect(operations.withTransaction(status ->
                Flux.from(authorRepository.save(new Author("Stephen King")))
                        .flatMap((author -> bookRepository.saveAll([
                                new Book("The Stand", 1000, author),
                                new Book("The Shining", 400, author)
                        ])))
                        .thenMany(Flux.from(authorRepository.save(new Author("James Patterson"))))
                        .flatMap((author ->
                                bookRepository.save(new Book("Along Came a Spider", 300, author))
                        )).then()
        )).block()
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

    def cleanupSpec() {
        container.stop()
    }

    void "test list books"() {
        when:
        List<Book> list = bookClient.list()

        then:
        list.size() == 5
    }

    @Override
    Map<String, String> getProperties() {
        container = new PostgreSQLContainer<>("postgres:10")
        container.start()
        return CollectionUtils.mapOf(
                "datasources.default.url", container.getJdbcUrl(),
                "datasources.default.username", container.getUsername(),
                "datasources.default.password", container.getPassword(),
                "datasources.default.database", container.getDatabaseName(),
                "r2dbc.datasources.default.host", container.getHost(),
                "r2dbc.datasources.default.port", container.getFirstMappedPort(),
                "r2dbc.datasources.default.driver", "postgres",
                "r2dbc.datasources.default.username", container.getUsername(),
                "r2dbc.datasources.default.password", container.getPassword(),
                "r2dbc.datasources.default.database", container.getDatabaseName()
        )
    }

    @Client("/books")
    static interface BookClient {
        @Get("/")
        List<Book> list()
    }
}
