package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExecuteOn(TaskExecutors.IO)
@Controller("/books")
public class BookController {

    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Post
    Mono<BookDto> save(String title, int pages) {
        return bookRepository.save(new Book(title, pages)).map(BookDto::new);
    }

    @Get("/{id}")
    Mono<BookDto> findOne(Long id) {
        return bookRepository.findById(id).map(BookDto::new);
    }

    @Get
    Flux<BookDto> findAll() {
        return bookRepository.findAll().map(BookDto::new);
    }

    @Delete
    Mono<Void> deleteAll() {
        return bookRepository.deleteAll().then();
    }

}
