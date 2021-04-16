package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller("/authors")
class AuthorController(private val repository: AuthorRepository) {
    @Get
    fun all(): Flux<Author> { // <1>
        return repository.findAll()
    }

    @Get("/id")
    fun get(id: Long): Mono<Author> { // <2>
        return repository.findById(id)
    }
}