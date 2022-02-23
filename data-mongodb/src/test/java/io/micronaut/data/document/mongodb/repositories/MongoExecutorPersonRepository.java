package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.mongodb.annotation.MongoAggregateOptions;
import io.micronaut.data.mongodb.annotation.MongoDeleteOptions;
import io.micronaut.data.mongodb.annotation.MongoFindOptions;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import io.micronaut.data.mongodb.repository.MongoQueryExecutor;
import io.micronaut.data.repository.CrudRepository;

@MongoAggregateOptions(bypassDocumentValidation = true, allowDiskUse = true)
@MongoFindOptions(batchSize = 3, allowDiskUse = true)
@MongoDeleteOptions()
@MongoUpdateOptions(bypassDocumentValidation = true)
@MongoRepository
public interface MongoExecutorPersonRepository extends CrudRepository<Person, String>, MongoQueryExecutor<Person> {
}
