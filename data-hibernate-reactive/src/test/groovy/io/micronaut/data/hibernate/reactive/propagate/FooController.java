package io.micronaut.data.hibernate.reactive.propagate;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@Controller("/")
public class FooController {

    @Inject
    private RequestContext requestContext;

    @Inject
    private FooService service;

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

    @Serdeable
    public record CreateRequest(Long id, String name) {}

}
