package example;

import io.micronaut.runtime.Micronaut;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest(contextBuilder = EagerStartSpec.MyApplicationContextBuilder.class)
class EagerStartSpec {

    @Inject
    BookRepository bookRepository;

    @AfterEach
    public void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void testSimpleOp() {
        Book book = new Book("The Stand", 1000);
        bookRepository.save(book);
        ObjectId id = book.getId();
        assertNotNull(id);

        book = bookRepository.findById(id).orElse(null);
        assertNotNull(book);
    }

    public static class MyApplicationContextBuilder extends Micronaut {

        {
            eagerInitSingletons(true);
        }

    }

}


