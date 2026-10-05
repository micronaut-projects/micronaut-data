package example;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
class BookControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    PagedBookRepository bookRepository;

    @BeforeEach
    void setup() {
        bookRepository.saveAll(List.of(
            new Book("The Stand", 1000),
            new Book("The Shining", 600),
            new Book("The Power of the Dog", 500),
            new Book("The Border", 700),
            new Book("Along Came a Spider", 300),
            new Book("Pet Cemetery", 400),
            new Book("A Game of Thrones", 900),
            new Book("A Clash of Kings", 1100)
        ));
    }

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void testPageableBinding() {
        // tag::request[]
        Page<BookSummary> page = client.toBlocking().retrieve(
            HttpRequest.GET("/books?page=1&size=3&sort=pages,desc&sort=title"), // <1>
            Argument.of(Page.class, BookSummary.class) // <2>
        );
        // end::request[]

        assertEquals(8, page.getTotalSize());
        assertEquals(3, page.getTotalPages());
        assertEquals(1, page.getPageNumber());
        assertEquals(3, page.getSize());
        assertEquals(
            List.of(Sort.Order.desc("pages"), Sort.Order.asc("title")),
            page.getPageable().getOrderBy()
        );
        assertEquals(
            List.of("The Border", "The Shining", "The Power of the Dog"),
            page.getContent().stream().map(BookSummary::title).toList()
        );
    }

    @Test
    void testDefaults() {
        Page<BookSummary> page = client.toBlocking().retrieve(
            HttpRequest.GET("/books"),
            Argument.of(Page.class, BookSummary.class)
        );
        // no parameters: the first page, the default page size (the max page size: 100) and no sorting
        assertEquals(0, page.getPageNumber());
        assertEquals(100, page.getSize());
        assertEquals(8, page.getContent().size());
        assertEquals(List.of(), page.getPageable().getOrderBy());

        page = client.toBlocking().retrieve(
            HttpRequest.GET("/books?size=1000&sort=title"),
            Argument.of(Page.class, BookSummary.class)
        );
        // the requested size is capped by the max page size
        assertEquals(100, page.getSize());
        assertEquals("A Clash of Kings", page.getContent().get(0).title());
    }
}
