package io.micronaut.data.r2dbc.postgres.http;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;

@Singleton
public class SampleServiceImpl implements SampleService {

    private final SampleRepository sampleRepository;

    @Inject
    public SampleServiceImpl(SampleRepository sampleRepository) {
        super();
        this.sampleRepository = sampleRepository;
    }

    @Override
    @Transactional
    public Mono<SampleEntity> save(SampleEntity entity) {
        return sampleRepository.save(entity);
    }

    @Override
    @Transactional
    public Mono<SampleEntity> findById(Long id) {
        return sampleRepository.findById(id);
    }

}
