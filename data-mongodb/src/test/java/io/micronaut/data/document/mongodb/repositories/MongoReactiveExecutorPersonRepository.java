package io.micronaut.data.document.mongodb.repositories;

import com.mongodb.client.model.ReturnDocument;
import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.mongodb.annotation.MongoAggregateOptions;
import io.micronaut.data.mongodb.annotation.MongoDeleteOptions;
import io.micronaut.data.mongodb.annotation.MongoFindOptions;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateReturningQuery;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import io.micronaut.data.mongodb.repository.MongoReactiveQueryExecutor;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.core.annotation.Introspected;
import reactor.core.publisher.Mono;

@MongoAggregateOptions(bypassDocumentValidation = true, allowDiskUse = true)
@MongoFindOptions(batchSize = 3, allowDiskUse = true)
@MongoDeleteOptions()
@MongoUpdateOptions(bypassDocumentValidation = true)
@MongoRepository
public interface MongoReactiveExecutorPersonRepository extends ReactorCrudRepository<Person, String>, MongoReactiveQueryExecutor<Person> {

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.AFTER)
    Mono<Person> updateCustomReturning(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.BEFORE)
    Mono<Person> updateCustomReturningBefore(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.AFTER)
    Mono<Person> updateCustomReturningAfterWithOptions(String id, String newName, com.mongodb.client.model.FindOneAndUpdateOptions options);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.AFTER)
    Mono<PersonNameDto> updateCustomReturningDto(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}")
    Mono<Person> updateCustomReturningDefault(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", project = "{name: 1}", returnDocument = ReturnDocument.AFTER)
    Mono<String> updateCustomReturningNameProjection(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{name:{$eq: :name}}", sort = "{age: 1}", returnDocument = ReturnDocument.BEFORE)
    Mono<Person> updateCustomReturningSortedBefore(String name, String newName);

    @Introspected
    record PersonNameDto(String name) {
    }
}
