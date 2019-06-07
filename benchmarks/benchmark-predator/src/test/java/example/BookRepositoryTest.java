package example;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@MicronautTest
public class BookRepositoryTest {

    @Inject
    BookRepository bookRepository;

    @Test
    void bookCount() {

        Assertions.assertEquals(
                0,
                bookRepository.count()
        );
    }

}
