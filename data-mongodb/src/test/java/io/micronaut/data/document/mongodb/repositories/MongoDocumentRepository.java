package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.entities.Document;
import io.micronaut.data.document.tck.repositories.DocumentRepository;
import io.micronaut.data.mongodb.annotation.MongoRepository;

import java.util.List;

@MongoRepository
public interface MongoDocumentRepository extends DocumentRepository {

    List<Document> findByTagsArrayContains(String tag);

    List<Document> findByTagsArrayContains(List<String> tags);
}
