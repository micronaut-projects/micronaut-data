package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.entities.Document;
import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.document.tck.repositories.DocumentRepository;
import io.micronaut.data.mongodb.annotation.MongoFindQuery;
import io.micronaut.data.mongodb.annotation.MongoRepository;

import java.util.List;

@MongoRepository
public interface MongoDocumentRepository extends DocumentRepository {

    @MongoFindQuery(filter = "{'tags': :tag}")
    List<Document> findByTagsContainingSingleTag(String tag);

    @MongoFindQuery(filter = "{'tags': {$all: :tags}}")
    List<Document> findByTagsContainingMultipleTags(List<String> tags);
}
