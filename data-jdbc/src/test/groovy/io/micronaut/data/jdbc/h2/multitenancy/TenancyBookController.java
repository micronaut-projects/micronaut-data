package io.micronaut.data.jdbc.h2.multitenancy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;

@Requires(property = "spec.name", value = "TenancyBookControllerSpec")
@Controller("/books") // <1>
class TenancyBookController {
    private final TenancyBookRepository bookRepository;

    TenancyBookController(TenancyBookRepository bookRepository) { // <2>
        this.bookRepository = bookRepository;
    }

    @ExecuteOn(TaskExecutors.BLOCKING) // <3>
    @Get
        // <4>
    List<TenancyBook> index() {
        return bookRepository.findAll();
    }
}
