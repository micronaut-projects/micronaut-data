package example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BookRepositoryTest {

	@Autowired
	BookRepository bookRepository;

	@Test
	void bookCount() {

		Assertions.assertEquals(
				0,
				bookRepository.count()
		);
	}

}
