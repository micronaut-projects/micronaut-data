package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.repository.CrudRepository;

@CosmosRepository
public interface CosmosExecutorPersonRepository extends CrudRepository<Person, String>
    //MongoQueryExecutor<Person>
{
}
