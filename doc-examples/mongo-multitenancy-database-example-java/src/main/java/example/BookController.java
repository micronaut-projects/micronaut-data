package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.bson.types.ObjectId;

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
    Optional<BookDto> findOne(String id) {
        return bookRepository.findById(new ObjectId(id)).map(BookDto::new);
    }

    @Get
    List<BookDto> findAll() {
        List<BookDto> collect = bookRepository.findAll().stream().map(BookDto::new).toList();
        System.out.println(ServerRequestContext.currentRequest().get().getHeaders().get("tenantId") + " " + collect);
        return collect;
    }

    @Delete
    void deleteAll() {
        bookRepository.deleteAll();
    }

}
