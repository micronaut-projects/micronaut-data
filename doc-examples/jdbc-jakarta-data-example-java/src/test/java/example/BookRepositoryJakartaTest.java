package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.data.Sort;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.inject.Inject;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

@MicronautTest(transactional = false)
class BookRepositoryJakartaTest {

    // tag::inject[]
	@Inject BookRepository bookRepository;
    // end::inject[]

	@Inject AbstractBookRepository abstractBookRepository;

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
	void testCrud() {
		assertNotNull(bookRepository);

		// Create: Save a new book
        // tag::save[]
		Book book = new Book("The Stand", 1000);
		bookRepository.save(book);
        // end::save[]
		Long id = book.getId();
		assertNotNull(id);

		// Read: Read a book from the database
        // tag::read[]
		book = bookRepository.findById(id).orElse(null);
        // end::read[]
		assertNotNull(book);
		assertEquals("The Stand", book.getTitle());

		// Check the count
		assertEquals(1, bookRepository.count());
		assertTrue(bookRepository.findAll().iterator().hasNext());

		// Update: Update the book and save it again
        // tag::update[]
		bookRepository.update(book.getId(), "Changed");
        // end::update[]
		book = bookRepository.findById(id).orElse(null);
		assertEquals("Changed", book.getTitle());

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
				new Book("The Stand", 1000),
				new Book("The Shining", 600),
				new Book("The Power of the Dog", 500),
				new Book("The Border", 700),
				new Book("Along Came a Spider", 300),
				new Book("Pet Cemetery", 400),
				new Book("A Game of Thrones", 900),
				new Book("A Clash of Kings", 1100)
		));
		// end::saveall[]

		// tag::pageable[]
		Page<Book> slice = bookRepository.list(PageRequest.ofSize(3));
		List<Book> resultList =
				bookRepository.findByPagesGreaterThan(500, PageRequest.ofSize(3));
		Page<Book> page = bookRepository.findByTitleLike("The%", PageRequest.ofSize(3));
		// end::pageable[]

		assertEquals(
				3,
				slice.numberOfElements()
		);
		assertEquals(
				3,
				resultList.size()
		);
		assertEquals(
				3,
				page.numberOfElements()
		);
		assertEquals(
				2,
				page.totalPages()
		);

		List<Book> results = abstractBookRepository.findByTitle("The Shining");

		assertEquals(1, results.size());
	}

    @Test
    void testCursoredPageable() {
        bookRepository.saveAll(Arrays.asList(
            new Book("The Stand", 1000),
            new Book("The Shining", 600),
            new Book("The Power of the Dog", 500),
            new Book("The Border", 700),
            new Book("Along Came a Spider", 300),
            new Book("Pet Cemetery", 400),
            new Book("A Game of Thrones", 900),
            new Book("A Clash of Kings", 1100)
        ));

        // tag::cursored-pageable[]
        Sort<Object> order = Sort.asc("title");
        CursoredPage<Book> page =  // <1>
            bookRepository.find(PageRequest.ofSize(5), order);
        CursoredPage<Book> page2 = bookRepository.find(page.nextPageRequest(), order); // <2>
        CursoredPage<Book> pageByPagesBetween = // <3>
            bookRepository.findByPagesBetween(400, 700, PageRequest.ofSize(3));
        Page<Book> pageByTitleStarts = // <4>
            bookRepository.findByTitleStartingWith("The", PageRequest.ofSize( 3));
        // end::cursored-pageable[]

        assertEquals(
            5,
            page.numberOfElements()
        );
        assertEquals(
            3,
            page2.numberOfElements()
        );
        assertEquals(
            3,
            pageByPagesBetween.numberOfElements()
        );
        assertEquals(
            3,
            pageByTitleStarts.numberOfElements()
        );
    }

	@Test
	void testDto() {
		bookRepository.save(new Book("The Shining", 400));
		BookDTO book = bookRepository.findOne("The Shining");

		assertEquals("The Shining", book.getTitle());
	}

    @Test
    void testExpressions() {
        assertEquals(0, bookRepository.count());

        Book book = new Book("The Stand", 1000);
        bookRepository.insertCustomExp(book);

        // Micronaut Data JDBC supports updating ID for custom query entity updates

        book = bookRepository.findById(book.getId()).orElse(null);
        assertNotNull(book);
        assertEquals("The StandABC", book.getTitle()); // Modified by expression

        assertEquals(1, bookRepository.count());
        assertTrue(bookRepository.findAll().iterator().hasNext());
    }

}
