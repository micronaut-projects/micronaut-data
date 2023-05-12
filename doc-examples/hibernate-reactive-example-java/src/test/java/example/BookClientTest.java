package example;

import io.micronaut.data.model.Page;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
public class BookClientTest {

    @Inject
    BookClient bookClient;

    @Test
    void testBookClient() {
        bookClient.save(new Book("The Stand", 1000)).block();
        bookClient.save(new Book("The Shining", 600)).block();
        bookClient.save(new Book("It", 800)).block();
        bookClient.save(new Book("The Institute", 700)).block();
        Page<Book> page = bookClient.findByTitleLike("The%", 0, 10, null).block();

        Assertions.assertEquals(
            3,
            page.getContent().size()
        );

        page = bookClient.findByTitleLike("The%", 0, 2, "title, asc").block();

        Assertions.assertEquals(
            2,
            page.getContent().size()
        );
    }
}
