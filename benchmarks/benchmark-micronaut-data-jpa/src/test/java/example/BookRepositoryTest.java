package example;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BookRepositoryTest {

    private BookRepository bookRepository;
    private ApplicationContext context;

    @BeforeAll
    void setup() {
        this.context = ApplicationContext.run();
        this.bookRepository = context.getBean(BookRepository.class);
    }

    @AfterAll
    void cleanup() {
        this.context.close();
    }

    @Test
    void bookCount() {

        Assertions.assertEquals(
                0,
                bookRepository.count()
        );
    }

}
