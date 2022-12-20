package io.micronaut.data.document.tck.repositories;

import io.micronaut.data.document.tck.entities.Document;
import io.micronaut.data.repository.CrudRepository;

public interface DocumentRepository extends CrudRepository<Document, String> {
}
