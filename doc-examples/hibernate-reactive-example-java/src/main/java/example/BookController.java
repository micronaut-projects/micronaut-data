
package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

@Controller("/books")
public class BookController {
    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Get("/{title}")
    Mono<Page<Book>> findByTitleLike(String title, Pageable pageable) {
        return bookRepository.findByTitleLike(title, pageable);
    }

    @Post("/")
    Mono<Book> save(@Valid Book book) {
        return bookRepository.save(book);
    }
}
