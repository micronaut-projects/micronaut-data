package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.mongodb.annotation.MongoAggregateOptions;
import io.micronaut.data.mongodb.annotation.MongoDeleteOptions;
import io.micronaut.data.mongodb.annotation.MongoFindOptions;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import io.micronaut.data.mongodb.repository.MongoQueryExecutor;
import io.micronaut.data.mongodb.repository.MongoReactiveQueryExecutor;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

@MongoAggregateOptions(bypassDocumentValidation = true, allowDiskUse = true)
@MongoFindOptions(batchSize = 3, allowDiskUse = true)
@MongoDeleteOptions()
@MongoUpdateOptions(bypassDocumentValidation = true)
@MongoRepository
public interface MongoReactiveExecutorPersonRepository extends ReactorCrudRepository<Person, String>, MongoReactiveQueryExecutor<Person> {
}
