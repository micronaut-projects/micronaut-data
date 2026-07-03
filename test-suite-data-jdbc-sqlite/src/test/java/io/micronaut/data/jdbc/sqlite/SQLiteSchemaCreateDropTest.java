package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SQLiteSchemaCreateDropTest {

    @Test
    void bookIsCreated() {
        try (ApplicationContext context = ApplicationContext.run(createProperties())) {
            BookRepository bookRepository = context.getBean(SQLiteBookRepository.class);
            Book book = new Book();
            book.setTitle("title");
            bookRepository.save(book);

            assertEquals(1, bookRepository.count());
        }
    }

    @Test
    void bookWasDropped() {
        try (ApplicationContext context = ApplicationContext.run(createProperties())) {
            BookRepository bookRepository = context.getBean(SQLiteBookRepository.class);
            assertEquals(0, bookRepository.count());
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqliteschemacreatedrop", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE_DROP");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite,io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}
