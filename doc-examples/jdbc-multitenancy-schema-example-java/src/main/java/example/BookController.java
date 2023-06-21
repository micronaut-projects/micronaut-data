package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ExecuteOn(TaskExecutors.IO)
@Controller("/books")
public class BookController {

    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Post
    BookDto save(String title, int pages) {
        return new BookDto(bookRepository.save(new Book(title, pages)));
    }

    @Get("/{id}")
    Optional<BookDto> findOne(Long id) {
        return bookRepository.findById(id).map(BookDto::new);
    }

    @Get
    List<BookDto> findAll() {
        return bookRepository.findAll().stream().map(BookDto::new).collect(Collectors.toList());
    }

    @Delete
    void deleteAll() {
        bookRepository.deleteAll();
    }

}
