package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import java.util.Optional

@ExecuteOn(TaskExecutors.IO)
@Controller("/books")
class BookController(private val bookRepository: BookRepository) {

    @Post
    fun save(title: String, pages: Int): BookDto =
        BookDto(bookRepository.save(Book(title = title, pages = pages)))

    @Get("/{id}")
    fun findOne(id: Long): Optional<BookDto> =
        bookRepository.findById(id).map { BookDto(it) }

    @Get
    fun findAll(): List<BookDto> =
        bookRepository.findAll().map { BookDto(it) }

    @Get("/withoutTenancy")
    fun findAllWithoutTenancy(): List<BookDto> =
        bookRepository.`findAll$WithoutTenancy`().map { BookDto(it) }

    @Delete
    fun deleteAll() {
        bookRepository.deleteAll()
    }

}
