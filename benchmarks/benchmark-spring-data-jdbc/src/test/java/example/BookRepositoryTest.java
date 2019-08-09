package example;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BookRepositoryTest {

	@Autowired BookRepository bookRepository;

	@Test
	void bookCount() {

		Assertions.assertEquals(0, bookRepository.count());

		Book book = bookRepository.save(new Book("Title", 42));

		assertThat(bookRepository.findById(book.getId())).hasValue(book);
		assertThat(bookRepository.findByTitle("Title")).isEqualTo(book);
	}

}
