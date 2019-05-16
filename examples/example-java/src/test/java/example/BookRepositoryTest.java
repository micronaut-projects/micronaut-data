package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MicronautTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class BookRepositoryTest {

	@Inject BookRepository bookRepository;

	@Test
	void testCrud() {
		assertNotNull(bookRepository);

		// Create: Save a new book
		Book book = new Book();
		book.setTitle("The Stand");
		book.setPages(1000);
		bookRepository.save(book);

		Long id = book.getId();
		assertNotNull(id);

		// Read: Read a book from the database
		book = bookRepository.findById(id).orElse(null);
		assertNotNull(book);
		assertEquals("The Stand", book.getTitle());

		// Check the count
		assertEquals(1, bookRepository.count());
		assertTrue(bookRepository.findAll().iterator().hasNext());
		
		// Update: Update the book and save it again
		book.setTitle("Changed");
		bookRepository.save(book);
		book = bookRepository.findById(id).orElse(null);
		assertEquals("Changed", book.getTitle());

		// Delete: Delete the book
		bookRepository.deleteById(id);
		assertEquals(0, bookRepository.count());
	}
}