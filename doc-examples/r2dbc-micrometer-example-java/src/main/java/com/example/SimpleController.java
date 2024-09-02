package com.example;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.reactivestreams.Publisher;

@Controller
public class SimpleController {
    @Inject
    MyService service;
    @Inject
    R2dbcOperations operations;

    @Get("/hello")
    @Transactional
    public Publisher<String> hello() {
        return service.getMessage();
    }

    @Get("/helloManual")
    public Publisher<String> helloManual() {
        return operations.withTransaction(x -> service.getMessage());
    }
}
