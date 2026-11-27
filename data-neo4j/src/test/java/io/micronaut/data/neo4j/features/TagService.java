package io.micronaut.data.neo4j.features;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.Optional;

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

    @Transactional
    public Optional<String> renameAndFind(Long id, String name) {
        Optional<Tag> found = repository.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Tag renamed = new Tag(name);
        renamed.setId(id);
        repository.update(renamed);
        return repository.findById(id).map(Tag::getName);
    }
}
