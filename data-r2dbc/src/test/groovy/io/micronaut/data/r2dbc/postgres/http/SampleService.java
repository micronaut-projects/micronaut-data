package io.micronaut.data.r2dbc.postgres.http;

import reactor.core.publisher.Mono;

public interface SampleService {
    
    Mono<SampleEntity> save(SampleEntity entity);

    Mono<SampleEntity> findById(Long id);

}
