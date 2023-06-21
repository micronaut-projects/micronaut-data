package example

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import kotlinx.coroutines.flow.Flow
import jakarta.validation.Valid

@Controller("/books")
class BookController(private val bookRepository: BookRepository) {
    // tag::create[]
    @Post("/")
    suspend fun create(book: @Valid Book): Book {
        return bookRepository.save(book)
    }
    // end::create[]

    // tag::read[]
    @Get("/")
    fun all(): Flow<Book> {
        return bookRepository.findAll() // <1>
    }

    @Get("/{id}")
    suspend fun show(id: Long): Book? {
        return bookRepository.findById(id) // <2>
    }
    // end::read[]

    // tag::update[]
    @Put("/{id}")
    suspend fun update(id: Long, book: @Valid Book): Book {
        return bookRepository.update(book)
    }
    // end::update[]

    // tag::delete[]
    @Delete("/{id}")
    suspend fun delete(id: Long): HttpResponse<*> {
        val deleted = bookRepository.deleteById(id)
        return if (deleted > 0) HttpResponse.noContent<Any>() else HttpResponse.notFound<Any>()
    }
    // end::delete[]
}
