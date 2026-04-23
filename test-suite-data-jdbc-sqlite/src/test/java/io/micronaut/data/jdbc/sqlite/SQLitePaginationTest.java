package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties
class SQLitePaginationTest {

    @Inject
    SQLitePersonRepository personRepository;

    @Inject
    SQLiteBookRepository bookRepository;

    @BeforeEach
    void setup() {
        personRepository.deleteAll();

        List<Person> people = new ArrayList<>();
        for (int num = 0; num < 50; num++) {
            for (char c = 'A'; c <= 'Z'; c++) {
                Person person = new Person();
                person.setName(String.valueOf(c).repeat(5) + num);
                people.add(person);
            }
        }

        personRepository.saveAll(people);
    }

    @AfterEach
    void cleanup() {
        personRepository.deleteAll();
    }

    @Test
    void testSort() {
        var results = personRepository.listTop10(Sort.unsorted().order("name", Sort.Order.Direction.DESC));

        assertEquals(10, results.size());
        assertTrue(results.getFirst().getName().startsWith("Z"));
    }

    @Test
    void testPageableList() {
        assertEquals(1300, personRepository.count());

        Pageable pageable = Pageable.from(0, 10);
        Page<Person> page = personRepository.findAll(pageable);

        assertEquals(10, page.getContent().size());
        assertTrue(page.getContent().get(0).getName().startsWith("A"));
        assertTrue(page.getContent().get(1).getName().startsWith("B"));
        assertEquals(1300, page.getTotalSize());
        assertEquals(130, page.getTotalPages());
        assertEquals(10, page.nextPageable().getOffset());
        assertEquals(10, page.nextPageable().getSize());
        assertTrue(page.hasNext());
        assertTrue(page.hasTotalSize());
        assertFalse(page.hasPrevious());

        page = personRepository.findAll(page.nextPageable());

        assertEquals(10, page.getOffset());
        assertEquals(1, page.getPageNumber());
        assertTrue(page.getContent().get(0).getName().startsWith("K"));
        assertEquals(10, page.getContent().size());
        assertTrue(page.hasNext());
        assertTrue(page.hasPrevious());

        page = personRepository.findAll(page.previousPageable());

        assertEquals(0, page.getOffset());
        assertEquals(0, page.getPageNumber());
        assertTrue(page.getContent().get(0).getName().startsWith("A"));
        assertEquals(10, page.getContent().size());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
    }

    @Test
    void testPageableListWithoutTotalCount() {
        Pageable pageable = Pageable.from(0, 10).withoutTotal();
        Page<Person> page = personRepository.findAll(pageable);

        assertEquals(10, page.getContent().size());
        assertFalse(page.hasTotalSize());
        assertEquals(0, page.getTotalPages());
        assertEquals(-1, page.getTotalSize());
    }

    @Test
    void testPageableSort() {
        assertEquals(1300, personRepository.count());

        Page<Person> page = personRepository.findAll(
            Pageable.from(0, 10).order("name", Sort.Order.Direction.DESC)
        );

        assertEquals(10, page.getContent().size());
        assertTrue(page.getContent().get(0).getName().startsWith("Z"));
        assertTrue(page.getContent().get(1).getName().startsWith("Z"));
        assertEquals(1300, page.getTotalSize());
        assertEquals(130, page.getTotalPages());
        assertEquals(10, page.nextPageable().getOffset());
        assertEquals(10, page.nextPageable().getSize());

        page = personRepository.findAll(page.nextPageable());

        assertEquals(10, page.getOffset());
        assertEquals(1, page.getPageNumber());
        assertTrue(page.getContent().get(0).getName().startsWith("Z"));
    }

    @Test
    void testPageableFindBy() {
        Pageable pageable = Pageable.from(0, 10);
        Page<Person> page = personRepository.findByNameLike("A%", pageable);
        Page<Person> page2 = personRepository.findPeople("A%", pageable);
        var slice = personRepository.queryByNameLike("A%", pageable);

        assertEquals(0, page.getOffset());
        assertEquals(0, page.getPageNumber());
        assertEquals(50, page.getTotalSize());
        assertEquals(page.getTotalSize(), page2.getTotalSize());
        assertEquals(0, slice.getOffset());
        assertEquals(0, slice.getPageNumber());
        assertEquals(10, slice.getSize());
        assertFalse(slice.getContent().isEmpty());
        assertFalse(page.getContent().isEmpty());

        page = personRepository.findByNameLike("A%", page.nextPageable());

        assertEquals(10, page.getOffset());
        assertEquals(1, page.getPageNumber());
        assertEquals(50, page.getTotalSize());
        assertEquals(20, page.nextPageable().getOffset());
        assertEquals(2, page.nextPageable().getNumber());
    }

    @Test
    void testTotalSizeOfFindWithLeftJoin() {
        var books = bookRepository.saveAll(List.of(
            book("Book 1", 100),
            book("Book 2", 100)
        ));

        var page = bookRepository.findByTotalPagesGreaterThan(0, Pageable.from(0, books.size()));

        assertEquals(books.size(), page.getContent().size());
        assertEquals(books.size(), page.getTotalSize());

        bookRepository.deleteAll();
    }

    @Test
    void testPagingWithCriteriaAndLimit() {
        bookRepository.saveAll(List.of(
            book("Book 1", 100),
            book("Book 2", 100),
            book("Book 3", 100),
            book("Book 4", 200)
        ));

        var page = bookRepository.findBooksByTotalPages(100, Pageable.from(0, 2));

        assertEquals(3, page.getTotalSize());
        assertEquals(2, page.getContent().size());

        bookRepository.deleteAll();
    }

    private Book book(String title, int totalPages) {
        Book book = new Book();
        book.setTitle(title);
        book.setTotalPages(totalPages);
        return book;
    }
}
