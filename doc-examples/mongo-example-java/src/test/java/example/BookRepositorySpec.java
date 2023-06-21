package example;

import io.micronaut.context.BeanContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
				"{title:{$eq:{$mn_qp:0}}}", query);

	}
	// end::metadata[]

	@AfterEach
	public void cleanup() {
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
		ObjectId id = book.getId();
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
		Slice<Book> slice = bookRepository.list(Pageable.from(0, 3));
		List<Book> resultList =
				bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3));
		Page<Book> page = bookRepository.findByTitleRegex("The.*", Pageable.from(0, 3));
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
		bookRepository.save(new Book("The Shining", 400));
		BookDTO book = bookRepository.findOne("The Shining");

		assertEquals("The Shining", book.getTitle());
	}
}
