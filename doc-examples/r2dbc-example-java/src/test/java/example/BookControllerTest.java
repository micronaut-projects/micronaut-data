package example;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BookControllerTest {

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
        Flux.from(operations.withTransaction(status ->
            Flux.from(authorRepository.save(new Author("Stephen King")))
                    .flatMap((author -> bookRepository.saveAll(Arrays.asList(
                            new Book("The Stand", 1000, author),
                            new Book("The Shining", 400, author)
                    ))))
            .thenMany(Flux.from(authorRepository.save(new Author("James Patterson"))))
                .flatMap((author ->
                        bookRepository.save(new Book("Along Came a Spider", 300, author))
            )).then()
        )).collectList().block();
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
    static void cleanup(AuthorRepository authorRepository, BookRepository bookRepository) {
        authorRepository.deleteAll();
        bookRepository.deleteAll();
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

    @Client("/books")
    interface BookClient {
        @Get("/")
        List<Book> list();
    }
}
