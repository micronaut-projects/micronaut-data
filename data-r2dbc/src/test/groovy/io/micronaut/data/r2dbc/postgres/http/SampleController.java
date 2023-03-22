package io.micronaut.data.r2dbc.postgres.http;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.validation.Validated;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.net.URI;

@Controller("/samples")
@Validated
public class SampleController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleController.class);

    private final SampleService sampleService;

    @Inject
    public SampleController(SampleService sampleService) {
        super();
        this.sampleService = sampleService;
    }

    @Post(consumes = MediaType.APPLICATION_JSON)
    @Status(HttpStatus.CREATED)
    public Mono<HttpResponse<?>> add(@Valid @Body SampleEntity sampleRequest) {
        LOGGER.debug("Creating new {}", sampleRequest);
        return sampleService.save(sampleRequest)
                .doOnSuccess(s -> LOGGER.info("{} successfully created", s))
                .map(s -> HttpResponse.created(URI.create("/samples/" + s.getId())));
    }

    @Get(uri = "/{id}", produces = { MediaType.APPLICATION_JSON_STREAM, MediaType.APPLICATION_JSON })
    public Mono<SampleEntity> getById(Long id) {
        LOGGER.debug("Retrieving sample with id '{}'", id);
        return sampleService.findById(id).doOnSuccess(s -> LOGGER.info("Returned {}", s));
    }

}
