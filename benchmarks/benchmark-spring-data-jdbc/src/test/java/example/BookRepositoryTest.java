package example;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@SpringBootTest
@Transactional
class BookRepositoryTest {

	@Autowired BookRepository bookRepository;

	@BeforeEach
	void setup() {
		this.bookRepository.saveAll(Arrays.asList(
				new Book("The Stand", 1000),
				new Book("The Shining", 600),
				new Book("The Power of the Dog", 500),
				new Book("The Border", 700),
				new Book("Along Came a Spider", 300),
				new Book("Pet Cemetery", 400),
				new Book("A Game of Thrones", 900),
				new Book("A Clash of Kings", 1100)
		));
	}
	@Test
	void bookCount() {
		bookRepository.findByTitle("The Stand");
		Assertions.assertEquals(8, bookRepository.count());

		Book book = bookRepository.save(new Book("Title", 42));

		assertThat(bookRepository.findById(book.getId())).hasValue(book);
		assertThat(bookRepository.findByTitle("Title")).isEqualTo(book);
	}

}
