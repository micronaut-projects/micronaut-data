package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@Property(name = "spec.name", value = "BookRepositoryTest")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.transaction-manager", value = "springJdbc")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class BookRepositoryTest {

    @Inject
    AbstractBookRepository bookRepository;

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void testBooksJdbcTemplate() {
        bookRepository.saveAll(Arrays.asList(
            new Book(null,"The Stand", 1000),
            new Book(null,"The Shining", 600),
            new Book(null,"The Power of the Dog", 500),
            new Book(null,"The Border", 700),
            new Book(null,"Along Came a Spider", 300),
            new Book(null,"Pet Cemetery", 400),
            new Book(null,"A Game of Thrones", 900),
            new Book(null,"A Clash of Kings", 1100)
        ));

        List<Book> result = bookRepository.findByTitle("The Shining");
        assertEquals(1, result.size());

        assertNotNull(result.get(0).id());
        assertEquals("The Shining", result.get(0).title());
        assertEquals(600, result.get(0).pages());
    }
}
