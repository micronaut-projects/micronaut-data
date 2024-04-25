package io.micronaut.data.jdbc.h2.multitenancy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Status;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;

@Requires(property = "spec.name", value = "TenancyBookControllerSpec")
@Controller("/books")
class TenancyBookController {
    private final TenancyBookRepository bookRepository;

    TenancyBookController(TenancyBookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Get
    List<TenancyBook> index() {
        return bookRepository.findAll();
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Delete
    @Status(HttpStatus.NO_CONTENT)
    void delete() {
        bookRepository.deleteAll();
    }
}
