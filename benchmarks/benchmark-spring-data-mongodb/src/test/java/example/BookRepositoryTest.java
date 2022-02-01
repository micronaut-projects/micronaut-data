package example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;

@SpringBootTest
class BookRepositoryTest {

    @Autowired
    BookRepository bookRepository;

    private static MongoDBContainer mongoDbContainer = new MongoDBContainer(DockerImageName.parse("mongo").withTag("5"));

    @AfterAll
    public static void stopContainer() {
        mongoDbContainer.close();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        mongoDbContainer.start();
        registry.add("spring.data.mongodb.uri", mongoDbContainer::getReplicaSetUrl);
    }

    @BeforeEach
    void setup() {
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

    @Test
    void bookCount() {
        bookRepository.findByTitle("The Stand");
        Assertions.assertEquals(8, bookRepository.count());
//
//        Book book = bookRepository.save(new Book("Title", 42));
//
//        assertThat(bookRepository.findById(book.getId())).hasValue(book);
//        assertThat(bookRepository.findByTitle("Title")).isEqualTo(book);
    }

}
