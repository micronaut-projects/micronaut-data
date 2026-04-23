package io.micronaut.data.jdbc.sqlite.identity;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.identity")
@Property(name = "datasources.default.batch-generate", value = "true")
class SameIdentityRepositoryTest {

    @Inject
    MyBookRepository bookRepository;

    @Test
    void testGetBooks() {
        List<MyBook> books = bookRepository.getBooks();

        assertEquals("Title #1", books.get(0).getTitle());
        assertEquals("Title #2", books.get(1).getTitle());
    }

    @Test
    void testGetBooksDto() {
        List<MyBookDto> books = bookRepository.getBooksAsDto();

        assertEquals("Title #1", books.get(0).title());
        assertEquals("Title #2", books.get(1).title());
    }

    @Test
    void testGetBooksDto2() {
        List<MyBookDto2> books = bookRepository.getBooksAsDto2();

        assertEquals("Title #1", books.get(0).title());
        assertEquals("Title #2", books.get(1).title());
    }
}
