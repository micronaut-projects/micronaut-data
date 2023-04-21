package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositorySpec {

    // tag::inject[]
    @Inject
    BookRepository bookRepository;
    // end::inject[]

    @AfterEach
    public void cleanup() {
        bookRepository.deleteAll().block();
    }

    @Test
    void testCrud() {
        // Create: Save a new book
        // tag::save[]
        Book book = new Book();
        book.setTitle("The Stand");
        book.setPages(1000);
        bookRepository.save(book).block();
        // end::save[]
        Long id = book.getId();
        assertNotNull(id);

        // Read: Read a book from the database
        // tag::read[]
        book = bookRepository.findById(id).block();
        // end::read[]
        assertNotNull(book);
        assertEquals("The Stand", book.getTitle());

        // Check the count
        assertEquals(1, bookRepository.count().block());
        assertTrue(bookRepository.findAll().toIterable().iterator().hasNext());

        // Update: Update the book and save it again
        // tag::update[]
        bookRepository.findByIdAndUpdate(id, foundBook -> {
            foundBook.setTitle("Changed");
        }).block();
        // end::update[]
        book = bookRepository.findById(id).block();
        assertEquals("Changed", book.getTitle());

        // Delete: Delete the book
        // tag::delete[]
        bookRepository.deleteById(id).block();
        // end::delete[]
        assertEquals(0, bookRepository.count().block());
    }

    @Test
    void testPageable() {
        // tag::saveall[]
        bookRepository.saveAll(Arrays.asList(new Book("The Stand", 1000), new Book("The Shining", 600),
            new Book("The Power of the Dog", 500), new Book("The Border", 700),
            new Book("Along Came a Spider", 300), new Book("Pet Cemetery", 400), new Book("A Game of Thrones", 900),
            new Book("A Clash of Kings", 1100))).then().block();
        // end::saveall[]

        // tag::pageable[]
        Slice<Book> slice = bookRepository.list(Pageable.from(0, 3)).block();
        List<Book> resultList = bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3)).collectList().block();
        Page<Book> page = bookRepository.findByTitleLike("The%", Pageable.from(0, 3)).block();
        // end::pageable[]

        assertEquals(3, slice.getNumberOfElements());
        assertEquals(3, resultList.size());
        assertEquals(3, page.getNumberOfElements());
        assertEquals(4, page.getTotalSize());
    }

    @Test
    void testDto() {
        bookRepository.save(new Book("The Shining", 400)).block();
        BookDTO book = bookRepository.findOne("The Shining").block();

        assertEquals("The Shining", book.getTitle());
    }
}
