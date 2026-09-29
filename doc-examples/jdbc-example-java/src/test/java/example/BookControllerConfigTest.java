package example;

import io.micronaut.context.annotation.Property;
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
@Property(name = "micronaut.data.pageable.max-page-size", value = "5")
@Property(name = "micronaut.data.pageable.default-page-size", value = "2")
@Property(name = "micronaut.data.pageable.page-parameter-name", value = "p")
@Property(name = "micronaut.data.pageable.size-parameter-name", value = "limit")
@Property(name = "micronaut.data.pageable.sort-parameter-name", value = "orderBy")
@Property(name = "micronaut.data.pageable.sort-delimiter", value = ":")
@Property(name = "micronaut.data.pageable.sort-ignore-case", value = "true")
class BookControllerConfigTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    PagedBookRepository bookRepository;

    @BeforeEach
    void setup() {
        bookRepository.saveAll(List.of(
            new Book("the Stand", 1000),
            new Book("The Shining", 600),
            new Book("A Game of Thrones", 900)
        ));
    }

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void testCustomConfiguration() {
        Page<BookSummary> page = client.toBlocking().retrieve(
            HttpRequest.GET("/books?orderBy=title:desc"),
            Argument.of(Page.class, BookSummary.class)
        );
        // default-page-size applies when no size is requested
        assertEquals(0, page.getPageNumber());
        assertEquals(2, page.getSize());
        assertEquals(List.of(Sort.Order.desc("title", true)), page.getPageable().getOrderBy());
        assertEquals(List.of("the Stand", "The Shining"), page.getContent().stream().map(BookSummary::title).toList());

        page = client.toBlocking().retrieve(
            HttpRequest.GET("/books?p=1&limit=10&orderBy=title"),
            Argument.of(Page.class, BookSummary.class)
        );
        // the requested size is capped by max-page-size
        assertEquals(1, page.getPageNumber());
        assertEquals(5, page.getSize());
        assertEquals(List.of(), page.getContent());
    }
}
