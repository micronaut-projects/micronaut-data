package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@ExecuteOn(TaskExecutors.IO)
@Controller("/books")
class BookController {

    private final BookRepository bookRepository

    BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository
    }

    @Post
    BookDto save(String title, int pages) {
        return new BookDto(bookRepository.save(new Book(title, pages)))
    }

    @Get("/{id}")
    Optional<BookDto> findOne(Long id) {
        return bookRepository.findById(id).map { new BookDto(it) }
    }

    @Get
    List<BookDto> findAll() {
        return bookRepository.findAll().collect { new BookDto(it) }
    }

    @Delete
    void deleteAll() {
        bookRepository.deleteAll()
    }

}
