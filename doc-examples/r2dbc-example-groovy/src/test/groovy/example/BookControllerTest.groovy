package example

import io.micronaut.core.util.CollectionUtils
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.reactivex.Flowable
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class BookControllerTest extends Specification implements TestPropertyProvider {

    static MySQLContainer<?> container

    @Inject BookClient bookClient

    @Shared @Inject R2dbcOperations operations
    @Shared @Inject AuthorRepository authorRepository
    @Shared @Inject BookRepository bookRepository

    def setupSpec() {
        // tag::programmatic-tx[]
        Mono.from(operations.withTransaction(status ->
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
        Flowable.fromPublisher(operations.withTransaction(status -> // <1>
                Flowable.fromPublisher(authorRepository.save(new Author("Michael Crichton")))
                        .flatMap((author -> operations.withTransaction(status, (s) -> // <2>
                                bookRepository.saveAll([
                                        new Book("Jurassic Park", 300, author),
                                        new Book("Disclosure", 400, author)
                                ]))))
        )).blockingSubscribe()
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
        container = new MySQLContainer<>(DockerImageName.parse("mysql").withTag("5"))
        container.start()
        return CollectionUtils.mapOf(
                "datasources.default.url", container.getJdbcUrl(),
                "datasources.default.username", container.getUsername(),
                "datasources.default.password", container.getPassword(),
                "datasources.default.database", container.getDatabaseName(),
                "r2dbc.datasources.default.host", container.getHost(),
                "r2dbc.datasources.default.port", container.getFirstMappedPort(),
                "r2dbc.datasources.default.driver", "mysql",
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
