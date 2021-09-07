package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositorySpec implements TestPropertyProvider {

    @ClassRule
    public static OracleContainer oracleContainer = new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:18"))
            .withEnv("ORACLE_PASSWORD", "password")
            .withPassword("password");

    @Inject
    BookRepository bookRepository;

    @Test
    void testCrud() {
        assertNotNull(bookRepository);

        // Create: Save a new book
        Book book = new Book("The Stand", 1000);
        book.setYearsReleased(new int[]{1988, 2000});
        bookRepository.save(book);
        Long id = book.getId();
        assertNotNull(id);

        // Read: Read a book from the database
        book = bookRepository.findById(id).orElse(null);
        assertNotNull(book);
        assertEquals("The Stand", book.getTitle());
        assertEquals(new int[]{1988, 2000}, book.getYearsReleased());
        assertNull(book.getYearsBestBook());
    }

    @Override
    public Map<String, String> getProperties() {
        oracleContainer.start();
        Map<String, String> map = new HashMap<>();
        map.put("datasources.default.url", oracleContainer.getJdbcUrl());
        map.put("datasources.default.username", oracleContainer.getUsername());
        map.put("datasources.default.password", oracleContainer.getPassword());
        map.put("datasources.default.driverClassName", oracleContainer.getDriverClassName());
        return map;
    }
}