package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Page;
import io.micronaut.data.tck.entities.Shelf;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties
class SQLiteUnidirectionalToManyJoinTest {

    @Inject
    SQLiteShelfRepository shelfRepository;

    @Inject
    SQLiteBookRepository bookRepository;

    @Inject
    SQLitePageRepository pageRepository;

    @Inject
    SQLiteShelfBookRepository shelfBookRepository;

    @Inject
    SQLiteBookPageRepository bookPageRepository;

    @Test
    void testUnidirectionalJoin() {
        bookRepository.deleteAll();

        Shelf shelf = new Shelf();
        shelf.setShelfName("Some Shelf");

        Book b1 = new Book();
        b1.setTitle("The Stand");
        b1.setTotalPages(1000);
        Page p1 = new Page();
        p1.setNum(10);
        b1.getPages().add(p1);
        Page p2 = new Page();
        p2.setNum(20);
        b1.getPages().add(p2);

        Book b2 = new Book();
        b2.setTitle("The Shining");
        b2.setTotalPages(600);

        shelf.getBooks().add(b1);
        shelf.getBooks().add(b2);

        shelf = shelfRepository.save(shelf);

        for (Page page : b1.getPages()) {
            assertNotNull(page.getId());
        }
        for (Book book : shelf.getBooks()) {
            assertNotNull(book.getId());
        }

        shelf = shelfRepository.findById(shelf.getId()).orElse(null);

        assertNotNull(shelf);
        assertEquals("Some Shelf", shelf.getShelfName());
        assertFalse(shelf.getBooks().isEmpty());
    }
}
