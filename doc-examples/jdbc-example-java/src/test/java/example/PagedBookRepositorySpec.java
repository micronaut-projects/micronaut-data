package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class PagedBookRepositorySpec {

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
    void testExplicitQueryPagination() {
        // tag::explicit-usage[]
        Pageable pageable = Pageable.from(1, 2, Sort.of(Sort.Order.desc("pages"))); // <1>
        Page<Book> page = bookRepository.findLongBooks(500, pageable); // <2>
        Slice<Book> slice = bookRepository.sliceLongBooks(500, pageable); // <3>
        List<Book> list = bookRepository.listLongBooks(500, Pageable.from(0, 3)); // <4>
        List<Book> sorted = bookRepository.listLongBooksSorted(500, Sort.of(Sort.Order.asc("title"))); // <5>
        // end::explicit-usage[]

        assertEquals(5, page.getTotalSize());
        assertEquals(3, page.getTotalPages());
        assertEquals(List.of("A Game of Thrones", "The Border"), page.getContent().stream().map(Book::getTitle).toList());
        assertEquals(List.of("A Game of Thrones", "The Border"), slice.getContent().stream().map(Book::getTitle).toList());
        assertEquals(3, list.size());
        assertEquals(
            List.of("A Clash of Kings", "A Game of Thrones", "The Border", "The Shining", "The Stand"),
            sorted.stream().map(Book::getTitle).toList()
        );
    }

    @Test
    void testNativeQueryPagination() {
        // tag::native-usage[]
        Page<Book> page = bookRepository.searchByTitle("The%",
            Pageable.from(0, 3, Sort.of(Sort.Order.asc("b.pages")))); // <1>
        // end::native-usage[]

        assertEquals(4, page.getTotalSize());
        assertEquals(2, page.getTotalPages());
        assertTrue(page.hasNext());
        assertEquals(List.of("The Power of the Dog", "The Shining", "The Border"), page.getContent().stream().map(Book::getTitle).toList());

        Page<Book> last = bookRepository.searchByTitle("The%", page.nextPageable());
        assertEquals(List.of("The Stand"), last.getContent().stream().map(Book::getTitle).toList());
        assertFalse(last.hasNext());
    }
}
