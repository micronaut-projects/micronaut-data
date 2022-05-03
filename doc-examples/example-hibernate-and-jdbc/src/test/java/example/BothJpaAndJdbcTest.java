package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
@Disabled("https://github.com/micronaut-projects/micronaut-core/issues/7197")
public class BothJpaAndJdbcTest {

    private final BookJdbcRepository bookJdbcRepository;
    private final BookJpaRepository bookJpaRepository;

    public BothJpaAndJdbcTest(BookJdbcRepository bookJdbcRepository, BookJpaRepository bookJpaRepository) {
        this.bookJdbcRepository = bookJdbcRepository;
        this.bookJpaRepository = bookJpaRepository;
    }

    @Test
    void testHavingBothJpaAndJdbcActive() {
        final Book book = new Book();
        book.setTitle("The Stand");
        bookJpaRepository.saveAndFlush(book);
        assertTrue(
                bookJdbcRepository.findById(book.getId()).isPresent()
        );

        assertEquals(1, bookJdbcRepository.count());

    }
}
