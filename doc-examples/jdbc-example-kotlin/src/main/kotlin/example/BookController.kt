package example

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

// tag::controller[]
@Controller("/books")
class BookController(private val bookRepository: PagedBookRepository) {

    @Get // <1>
    fun list(pageable: Pageable): Page<BookSummary> = // <2>
        bookRepository.list(pageable) // <3>
}
// end::controller[]
