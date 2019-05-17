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
class BookRepositorySpec {

    // tag::inject[]
	@Inject BookRepository bookRepository;
    // end::inject[]

	@Test
	void testCrud() {
		assertNotNull(bookRepository);

		// Create: Save a new book
        // tag::save[]
		Book book = new Book();
		book.setTitle("The Stand");
		book.setPages(1000);
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
		book.setTitle("Changed");
		bookRepository.save(book);
        // end::update[]
		book = bookRepository.findById(id).orElse(null);
		assertEquals("Changed", book.getTitle());

		// Delete: Delete the book
        // tag::delete[]
		bookRepository.deleteById(id);
        // end::delete[]
		assertEquals(0, bookRepository.count());
	}
}