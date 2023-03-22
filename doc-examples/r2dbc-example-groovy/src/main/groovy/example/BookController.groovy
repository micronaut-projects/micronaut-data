package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

@Controller("/books")
class BookController {
    private final BookRepository bookRepository

    BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository
    }

     // tag::create[]
    @Post("/")
    Mono<Book> create(@Valid Book book) {
        return Mono.from(bookRepository.save(book))
    }
    // end::create[]

    // tag::read[]
    @Get("/")
    Flux<Book> all() {
        return bookRepository.findAll() // <1>
    }

    @Get("/{id}")
    Mono<Book> show(Long id) {
        return bookRepository.findById(id) // <2>
    }
    // end::read[]

    // tag::update[]
    @Put("/{id}")
    Mono<Book> update(@NotNull Long id, @Valid Book book) {
        return Mono.from(bookRepository.update(book))
    }
    // end::update[]

    // tag::delete[]
    @Delete("/{id}")
    Mono<HttpResponse<?>> delete(@NotNull Long id) {
        return Mono.from(bookRepository.deleteById(id))
                .map(deleted -> deleted > 0 ? HttpResponse.noContent() : HttpResponse.notFound())
    }
    // end::delete[]
}
