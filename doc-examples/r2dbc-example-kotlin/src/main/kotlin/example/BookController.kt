package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import io.reactivex.Single
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import javax.validation.Valid
import javax.validation.constraints.NotNull

@Controller("/books")
class BookController(private val bookRepository: BookRepository) {
    // tag::create[]
    @Post("/")
    fun create(book: @Valid Book): Single<Book> {
        return Single.fromPublisher(bookRepository.save(book))
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
    fun update(id: Long, book: @Valid Book): Single<Book> {
        return Single.fromPublisher(bookRepository.update(book))
    }
    // end::update[]

    // tag::delete[]
    @Delete("/{id}")
    fun delete(id: Long): Single<HttpResponse<*>> {
        return Single.fromPublisher(bookRepository.deleteById(id))
                .map { deleted: Long -> if (deleted > 0) HttpResponse.noContent() else HttpResponse.notFound<Any>() }
    }
    // end::delete[]
}