package example;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BookRepositoryTest {

    private BookRepository bookRepository;
    private ApplicationContext context;
    private MongoDBContainer mongoDBContainer;

    @BeforeAll
    void setup() {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo").withTag("5"));
        mongoDBContainer.start();
        Map<String, Object> props = new HashMap<>();
        props.put("mongodb.uri", mongoDBContainer.getReplicaSetUrl());
        this.context = ApplicationContext.run(props);
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
        mongoDBContainer.close();
    }

    @Test
    void bookCount() {
        bookRepository.findByTitle("The Stand");
        Assertions.assertEquals(
                8,
                bookRepository.count()
        );
    }

}
