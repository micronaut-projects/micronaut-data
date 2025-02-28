package io.micronaut.data.tck.repositories;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Document;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends CrudRepository<Document, UUID> {

    @Override
    @Join(value = "type", type = Join.Type.LEFT_FETCH)
    @NonNull Optional<Document> findById(@NonNull UUID id);
}
