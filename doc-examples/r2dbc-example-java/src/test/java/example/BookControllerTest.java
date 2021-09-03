package example;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BookControllerTest implements TestPropertyProvider {

    static MySQLContainer<?> container;

    @Inject
    BookClient bookClient;

    @Inject
    R2dbcOperations operations;

    @Inject
    AuthorRepository authorRepository;

    @Inject
    BookRepository bookRepository;

    @BeforeAll
    static void setupData(R2dbcOperations operations, AuthorRepository authorRepository, BookRepository bookRepository) {
        // tag::programmatic-tx[]
        Mono.from(operations.withTransaction(status ->
            Flux.from(authorRepository.save(new Author("Stephen King")))
                    .flatMap((author -> bookRepository.saveAll(Arrays.asList(
                            new Book("The Stand", 1000, author),
                            new Book("The Shining", 400, author)
                    ))))
            .thenMany(Flux.from(authorRepository.save(new Author("James Patterson"))))
                .flatMap((author ->
                        bookRepository.save(new Book("Along Came a Spider", 300, author))
            )).then()
        )).block();
        // end::programmatic-tx[]

        // tag::programmatic-tx-status[]
        Flux.from(operations.withTransaction(status -> // <1>
                Flux.from(authorRepository.save(new Author("Michael Crichton")))
                        .flatMap((author -> operations.withTransaction(status, (s) -> // <2>
                                bookRepository.saveAll(Arrays.asList(
                                        new Book("Jurassic Park", 300, author),
                                        new Book("Disclosure", 400, author)
                                )))))
        )).collectList().block();
        // end::programmatic-tx-status[]
    }

    @AfterAll
    static void cleanup() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void testListBooks() {
        List<Book> list = bookClient.list();
        Assertions.assertEquals(
                5,
                list.size()
        );
    }

    @Test
    void testListBooksMicronautData() {
        List<Book> list = bookClient.list();
        Assertions.assertEquals(
                5,
                list.size()
        );
    }

    @Override
    public Map<String, String> getProperties() {
        container = new MySQLContainer<>(DockerImageName.parse("mysql/mysql-server:8.0").asCompatibleSubstituteFor("mysql"));
        container.start();
        return CollectionUtils.mapOf(
                "datasources.default.url", container.getJdbcUrl(),
                "datasources.default.username", container.getUsername(),
                "datasources.default.password", container.getPassword(),
                "datasources.default.database", container.getDatabaseName(),
                "datasources.default.driverClassName", container.getDriverClassName(),
                "r2dbc.datasources.default.host", container.getHost(),
                "r2dbc.datasources.default.port", container.getFirstMappedPort(),
                "r2dbc.datasources.default.driver", "mysql",
                "r2dbc.datasources.default.username", container.getUsername(),
                "r2dbc.datasources.default.password", container.getPassword(),
                "r2dbc.datasources.default.database", container.getDatabaseName()
        );
    }

    @Client("/books")
    interface BookClient {
        @Get("/")
        List<Book> list();
    }
}
