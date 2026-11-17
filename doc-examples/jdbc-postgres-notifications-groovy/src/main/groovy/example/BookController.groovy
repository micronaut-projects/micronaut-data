package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/books")
class BookController {
    private final BookCatalog catalog

    BookController(BookCatalog catalog) {
        this.catalog = catalog
    }

    @Get("/{id}")
    Optional<Book> find(Long id) {
        catalog.find(id)
    }
}
