package io.micronaut.data.jdbc;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SchemaCreateDropTest {

    protected final ApplicationContext context = ApplicationContext.run(new java.util.HashMap<>(getProperties()));

    protected abstract BookRepository getBookRepository();

    protected abstract java.util.Map<String, String> getProperties();

    @AfterAll
    void closeContext() {
        context.close();
    }

    @Test
    void bookIsCreated() {
        Book book = new Book();
        book.setTitle("title");
        getBookRepository().save(book);

        assertEquals(1, getBookRepository().count());
    }

    @Test
    void bookWasDropped() {
        assertEquals(0, getBookRepository().count());
    }
}
