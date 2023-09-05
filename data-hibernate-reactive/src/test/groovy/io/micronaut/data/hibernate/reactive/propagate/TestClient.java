package io.micronaut.data.hibernate.reactive.propagate;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import reactor.core.publisher.Mono;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

@Client("/")
public interface TestClient {
    @Post(uri = "/create", produces = APPLICATION_JSON)
    Mono<Foo> create(@Body FooController.CreateRequest request);

    @Post(uri = "/create-transactional", produces = APPLICATION_JSON)
    Mono<Foo> createTransactional(@Body FooController.CreateRequest request);

    @Get(uri = "/read", produces = APPLICATION_JSON)
    Mono<Foo> read(@QueryValue Long id);
}
