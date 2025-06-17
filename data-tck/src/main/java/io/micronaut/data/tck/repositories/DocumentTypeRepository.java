package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.DocumentType;

public interface DocumentTypeRepository extends CrudRepository<DocumentType, Long> {

    @Query("UPDATE document_type SET deleted = :deleted WHERE id = :id")
    void updateDeletedById(@Parameter Long id, @Parameter Boolean deleted);
}
