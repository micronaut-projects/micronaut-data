package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.Optional;

@Controller("/books")
public class BookController {
    private final BookCatalog catalog;

    public BookController(BookCatalog catalog) {
        this.catalog = catalog;
    }

    @Get("/{id}")
    public Optional<Book> find(Long id) {
        return catalog.find(id);
    }
}
