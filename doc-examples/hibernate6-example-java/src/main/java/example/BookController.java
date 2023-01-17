
package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Controller("/books")
public class BookController {
    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Get("/{title}")
    Page<Book> findByTitleLike(String title, Pageable pageable) {
        return bookRepository.findByTitleLike(title, pageable);
    }

    @Post("/")
    Book save(@Valid Book book) {
        return bookRepository.save(book);
    }
}
