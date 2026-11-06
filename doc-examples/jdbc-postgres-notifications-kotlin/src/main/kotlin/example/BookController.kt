package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import java.util.Optional

@Controller("/books")
class BookController(private val catalog: BookCatalog) {
    @Get("/{id}")
    fun find(id: Long): Optional<Book> = catalog.find(id)
}
