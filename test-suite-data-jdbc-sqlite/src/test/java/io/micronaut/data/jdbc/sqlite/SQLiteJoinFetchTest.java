package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.AuthorBooksDto;
import io.micronaut.data.tck.entities.BookDto;
import io.micronaut.data.tck.repositories.AuthorJoinTypeRepositories;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties
class SQLiteJoinFetchTest {

    @Inject
    ApplicationContext context;

    @Inject
    SQLiteBookRepository bookRepository;

    @Inject
    SQLiteAuthorRepository authorRepository;

    @Inject
    SQLiteAuthorJoinLeftFetchRepository authorJoinLeftFetchRepository;

    @Inject
    SQLiteAuthorJoinLeftRepository authorJoinLeftRepository;

    @Inject
    SQLiteAuthorJoinRightFetchRepository authorJoinRightFetchRepository;

    @Inject
    SQLiteAuthorJoinRightRepository authorJoinRightRepository;

    @Inject
    SQLiteAuthorJoinInnerRepository authorJoinInnerRepository;

    @BeforeEach
    void setup() {
        saveSampleBooks();
    }

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
    }

    @Test
    void leftJoinDoesNotFetchProjectedEntities() {
        var authors = authorJoinLeftRepository.findAll();

        assertFalse(authors.isEmpty());
        assertTrue(authors.getFirst().getBooks().isEmpty());
    }

    @Test
    void leftFetchJoinFetchesProjectedEntities() {
        var authors = authorJoinLeftFetchRepository.findAll();

        assertFalse(authors.isEmpty());
        var titles = authors.getFirst().getBooks().stream().map(book -> book.getTitle()).toList();
        assertTrue(titles.containsAll(Arrays.asList("The Stand", "Pet Sematary")));
    }

    @Test
    void rightJoinDoesNotFetchProjectedEntities() {
        var authors = authorJoinRightRepository.findAll();

        assertFalse(authors.isEmpty());
        assertTrue(authors.getFirst().getBooks().isEmpty());
    }

    @Test
    void rightFetchJoinFetchesProjectedEntities() {
        var authors = authorJoinRightFetchRepository.findAll();

        assertFalse(authors.isEmpty());
        var titles = authors.getFirst().getBooks().stream().map(book -> book.getTitle()).toList();
        assertTrue(titles.containsAll(Arrays.asList("The Stand", "Pet Sematary")));
    }

    @Test
    void fetchJoinFetchesProjectedEntities() {
        var authors = context.createBean(SQLiteAuthorJoinFetchRepository.class).findAll();

        assertFalse(authors.isEmpty());
        var titles = authors.getFirst().getBooks().stream().map(book -> book.getTitle()).toList();
        assertTrue(titles.containsAll(Arrays.asList("The Stand", "Pet Sematary")));
    }

    @Test
    void innerJoinDoesNotFetchProjectedEntities() {
        var authors = authorJoinInnerRepository.findAll();

        assertFalse(authors.isEmpty());
        assertTrue(authors.getFirst().getBooks().isEmpty());
    }

    private void saveSampleBooks() {
        bookRepository.saveAuthorBooks(Arrays.asList(
            new AuthorBooksDto("Stephen King", Arrays.asList(
                new BookDto("The Stand", 1000),
                new BookDto("Pet Sematary", 400)
            ))
        ));
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinFetchRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinInnerRepository extends AuthorJoinTypeRepositories.AuthorJoinInnerRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinLeftFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinLeftRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinRightFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinRightRepository extends AuthorJoinTypeRepositories.AuthorJoinRightRepository {
}
