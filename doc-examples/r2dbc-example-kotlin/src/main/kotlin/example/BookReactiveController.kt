package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import jakarta.validation.Valid

@Controller("/books2")
class BookReactiveController(private val bookRepository: BookReactiveRepository) {
    // tag::create[]
    @Post("/")
    fun create(book: @Valid Book): Mono<Book> {
        return Mono.from(bookRepository.save(book))
    }
    // end::create[]

    // tag::read[]
    @Get("/")
    fun all(): Flux<Book> {
        return bookRepository.findAll() // <1>
    }

    @Get("/{id}")
    fun show(id: Long): Mono<Book> {
        return bookRepository.findById(id) // <2>
    }
    // end::read[]

    // tag::update[]
    @Put("/{id}")
    fun update(id: Long, book: @Valid Book): Mono<Book> {
        return Mono.from(bookRepository.update(book))
    }
    // end::update[]

    // tag::delete[]
    @Delete("/{id}")
    fun delete(id: Long): Mono<HttpResponse<*>> {
        return Mono.from(bookRepository.deleteById(id))
                .map { deleted: Long -> if (deleted > 0) HttpResponse.noContent() else HttpResponse.notFound<Any>() }
    }
    // end::delete[]
}
