package example;

import io.micronaut.context.BeanContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

@MicronautTest
class BookRepositorySpec {

    // tag::inject[]
    @Inject BookRepository bookRepository;
    // end::inject[]

    // tag::metadata[]
    @Inject
    BeanContext beanContext;

    @Test
    void testAnnotationMetadata() {
        String query = beanContext.getBeanDefinition(BookRepository.class) // <1>
                .getRequiredMethod("find", String.class) // <2>
                .getAnnotationMetadata()
                .stringValue(Query.class) // <3>
                .orElse(null);

        assertEquals( // <4>
                "SELECT book_.`id`,book_.`date_created`,book_.`title`,book_.`pages` FROM `book` book_ WHERE (book_.`title` = ?)",
                query
        );

    }
    // end::metadata[]

    @Test
    void testCrud() {
        assertNotNull(bookRepository);

        // Create: Save a new book
        // tag::save[]
        Book book = new Book(null,null, "The Stand", 1000);
        book = bookRepository.save(book);
        // end::save[]
        Long id = book.id();
        assertNotNull(id);

        // Read: Read a book from the database
        // tag::read[]
        book = bookRepository.findById(id).orElse(null);
        // end::read[]
        assertNotNull(book);
        assertEquals("The Stand", book.title());

        // Check the count
        assertEquals(1, bookRepository.count());
        assertTrue(bookRepository.findAll().iterator().hasNext());

        // Update: Update the book and save it again
        // tag::update[]
        bookRepository.update(book.id(), "Changed");
        // end::update[]
        book = bookRepository.findById(id).orElse(null);
        assertEquals("Changed", book.title());

        // Delete: Delete the book
        // tag::delete[]
        bookRepository.deleteById(id);
        // end::delete[]
        assertEquals(0, bookRepository.count());
    }

    @Test
    void testPageable() {
        // tag::saveall[]
        bookRepository.saveAll(Arrays.asList(
                new Book(null, null,"The Stand", 1000),
                new Book(null, null,"The Shining", 600),
                new Book(null, null,"The Power of the Dog", 500),
                new Book(null, null,"The Border", 700),
                new Book(null, null,"Along Came a Spider", 300),
                new Book(null, null,"Pet Cemetery", 400),
                new Book(null, null,"A Game of Thrones", 900),
                new Book(null, null,"A Clash of Kings", 1100)
        ));
        // end::saveall[]

        // tag::pageable[]
        Slice<Book> slice = bookRepository.list(Pageable.from(0, 3));
        List<Book> resultList =
                bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3));
        Page<Book> page = bookRepository.findByTitleLike("The%", Pageable.from(0, 3));
        // end::pageable[]

        assertEquals(
                3,
                slice.getNumberOfElements()
        );
        assertEquals(
                3,
                resultList.size()
        );
        assertEquals(
                3,
                page.getNumberOfElements()
        );
        assertEquals(
                4,
                page.getTotalSize()
        );

    }

    @Test
    void testDto() {
        bookRepository.save(new Book(null, null,"The Shining", 400));
        BookDTO book = bookRepository.findOne("The Shining");

        assertEquals("The Shining", book.getTitle());
    }
}