package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller("/authors")
public class AuthorController {
    private final AuthorRepository repository;

    public AuthorController(AuthorRepository repository) {
        this.repository = repository;
    }

    @Get
    Flux<Author> all() { // <1>
        return repository.findAll();
    }

    @Get("/id")
    Mono<Author> get(Long id) { // <2>
        return repository.findById(id);
    }
}
