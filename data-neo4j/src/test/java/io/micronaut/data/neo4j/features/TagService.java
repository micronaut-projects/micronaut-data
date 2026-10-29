package io.micronaut.data.neo4j.features;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class TagService {

    private final TagRepository repository;

    public TagService(TagRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveAndFail(String name) {
        repository.save(new Tag(name));
        throw new IllegalStateException("Rolled back");
    }
}
