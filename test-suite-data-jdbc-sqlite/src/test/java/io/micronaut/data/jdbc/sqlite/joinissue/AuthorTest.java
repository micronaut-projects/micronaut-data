package io.micronaut.data.jdbc.sqlite.joinissue;

import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties
class AuthorTest {

    @Inject
    AuthorRepository authorRepository;

    @Test
    void test() {
        List<Author> authorList = List.of(
            new Author(null, "Joe Doe", Set.of(new Book(null, "History of nothing"))),
            new Author(null, "Jane Doe", Set.of(new Book(null, "History of everything"), new Book(null, "Doing awesome things")))
        );

        authorRepository.saveAll(authorList);

        Author author = authorRepository.queryByName("Joe Doe").orElse(null);
        assertNotNull(author);
        assertEquals("Joe Doe", author.name());
        assertEquals(1, author.books().size());

        List<Author> list = authorRepository.queryByNameContains("Doe");
        assertEquals(2, list.size());
        assertEquals("Joe Doe", list.get(0).name());
        assertEquals(1, list.get(0).books().size());
        assertEquals("Jane Doe", list.get(1).name());
        assertEquals(2, list.get(1).books().size());

        author = authorRepository.getOneByNameContains("Doe").orElse(null);
        assertNotNull(author);
        assertEquals("Joe Doe", author.name());
        assertEquals(1, author.books().size());

        author = authorRepository.getOneByNameContains("ne Doe").orElse(null);
        assertNotNull(author);
        assertEquals("Jane Doe", author.name());
        assertEquals(2, author.books().size());

        author = authorRepository.findByNameContains("Doe").orElse(null);
        assertNotNull(author);
        assertEquals("Joe Doe", author.name());
        assertEquals(1, author.books().size());

        author = authorRepository.findByNameContains("e Doe").orElse(null);
        assertNotNull(author);
        assertEquals("Joe Doe", author.name());
        assertEquals(1, author.books().size());

        author = authorRepository.findByNameContains("ne Doe").orElse(null);
        assertNotNull(author);
        assertEquals("Jane Doe", author.name());
        assertEquals(2, author.books().size());
    }
}
