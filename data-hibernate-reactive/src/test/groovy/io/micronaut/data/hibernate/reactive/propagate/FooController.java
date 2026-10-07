package io.micronaut.data.hibernate.reactive.propagate;

import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller("/")
public class FooController {

    @Inject
    private RequestContext requestContext;

    @Inject
    private FooService service;

    @Inject
    private FooRepository repository;

    @Post("/create")
    @Produces(MediaType.APPLICATION_JSON)
    Mono<Foo> create(@Body CreateRequest request) {
        requestContext.setId(request.id());
        requestContext.setName(request.name());
        return service.create();
    }

    @Post("/create-transactional")
    @Produces(MediaType.APPLICATION_JSON)
    Mono<Foo> createTransactional(@Body CreateRequest request) {
        requestContext.setId(request.id());
        requestContext.setName(request.name());
        return service.createTransactional();
    }

    @Get("/read")
    @Produces(MediaType.APPLICATION_JSON)
    Mono<Foo> read(@QueryValue Long id) {
        requestContext.setId(id);
        return service.read();
    }

    @Get("/list")
    @Connectable
    @Produces(MediaType.APPLICATION_JSON)
    Flux<Foo> list() {
        // Streamed: the server requests and completes the results from its own event loop
        return repository.findAll();
    }

    @Serdeable
    public record CreateRequest(Long id, String name) {}

}
