package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

// tag::controller[]
@Controller("/books")
public class BookController {

    private final PagedBookRepository bookRepository;

    public BookController(PagedBookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Get // <1>
    public Page<BookSummary> list(Pageable pageable) { // <2>
        return bookRepository.list(pageable); // <3>
    }
}
// end::controller[]
