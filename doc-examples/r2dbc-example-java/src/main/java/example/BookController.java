package example;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.reactivex.Single;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Controller("/books")
public class BookController {
    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

     // tag::create[]
    @Post("/")
    Single<Book> create(@Valid Book book) {
        return Single.fromPublisher(bookRepository.save(book));
    }
    // end::create[]

    // tag::read[]
    @Get("/")
    Flux<Book> all() {
        return bookRepository.findAll(); // <1>
    }

    @Get("/{id}")
    Mono<Book> show(Long id) {
        return bookRepository.findById(id); // <2>
    }
    // end::read[]

    // tag::update[]
    @Put("/{id}")
    Single<Book> update(@NotNull Long id, @Valid Book book) {
        return Single.fromPublisher(bookRepository.update(book));
    }
    // end::update[]

    // tag::delete[]
    @Delete("/{id}")
    Single<HttpResponse<?>> delete(@NotNull Long id) {
        return Single.fromPublisher(bookRepository.deleteById(id))
                .map(deleted -> deleted > 0 ? HttpResponse.noContent() : HttpResponse.notFound());
    }
    // end::delete[]
}
