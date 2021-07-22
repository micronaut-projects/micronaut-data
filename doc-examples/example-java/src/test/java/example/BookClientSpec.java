package example;

import io.micronaut.data.model.Page;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@MicronautTest
public class BookClientSpec {

    @Inject BookClient bookClient;

    @Test
    void testBookClient() {
        bookClient.save(new Book("The Stand", 1000));
        bookClient.save(new Book("The Shining", 600));
        bookClient.save(new Book("It", 800));
        bookClient.save(new Book("The Institute", 700));
        Page<Book> page = bookClient.findByTitleLike("The%", 0, 10, null);

        Assertions.assertEquals(
                3,
                page.getContent().size()
        );

        page = bookClient.findByTitleLike("The%", 0, 2, "title, asc");

        Assertions.assertEquals(
                2,
                page.getContent().size()
        );
    }
}
