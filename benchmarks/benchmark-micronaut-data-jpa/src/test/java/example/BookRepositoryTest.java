package example;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.*;

import java.util.Arrays;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BookRepositoryTest {

    private BookRepository bookRepository;
    private ApplicationContext context;

    @BeforeAll
    void setup() {
        this.context = ApplicationContext.run();
        this.bookRepository = context.getBean(BookRepository.class);
        this.bookRepository.saveAll(Arrays.asList(
                new Book("The Stand", 1000),
                new Book("The Shining", 600),
                new Book("The Power of the Dog", 500),
                new Book("The Border", 700),
                new Book("Along Came a Spider", 300),
                new Book("Pet Cemetery", 400),
                new Book("A Game of Thrones", 900),
                new Book("A Clash of Kings", 1100)
        ));
    }

    @AfterAll
    void cleanup() {
        this.context.close();
    }

    @Test
    void bookCount() {
        bookRepository.findByTitle("The Border");
        Assertions.assertEquals(
                8,
                bookRepository.count()
        );
    }

}
