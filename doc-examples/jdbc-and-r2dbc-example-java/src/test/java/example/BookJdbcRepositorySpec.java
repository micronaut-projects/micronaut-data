package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookJdbcRepositorySpec {

	@Inject BookJdbcRepository bookRepository;

	@Inject AbstractJdbcBookRepository abstractBookRepository;

	@Test
	void testCrud() {
		assertNotNull(bookRepository);

		// Create: Save a new book
		Book book = new Book("The Stand", 1000);
		bookRepository.save(book);
		Long id = book.getId();
		assertNotNull(id);

		// Read: Read a book from the database
		book = bookRepository.findById(id).orElse(null);
		assertNotNull(book);
		assertEquals("The Stand", book.getTitle());

		assertEquals(1, bookRepository.count());
		assertTrue(bookRepository.findAll().iterator().hasNext());

		// Update: Update the book and save it again
		bookRepository.update(book.getId(), "Changed");
		book = bookRepository.findById(id).orElse(null);
		assertEquals("Changed", book.getTitle());

		// Delete: Delete the book
		bookRepository.deleteById(id);
		assertEquals(0, bookRepository.count());
	}

	@Test
	void testPageable() {
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

		Slice<Book> slice = bookRepository.list(Pageable.from(0, 3));
		List<Book> resultList =
				bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3));
		Page<Book> page = bookRepository.findByTitleLike("The%", Pageable.from(0, 3));

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

		List<Book> results = abstractBookRepository.findByTitle("The Shining");

		assertEquals(1, results.size());
	}

}
