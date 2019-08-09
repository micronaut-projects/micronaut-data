package example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositoryTest {

	@Autowired
	BookRepository bookRepository;

	@BeforeAll
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
		bookRepository.findByTitle("The Border");
		Assertions.assertEquals(
				8,
				bookRepository.count()
		);
	}

}
