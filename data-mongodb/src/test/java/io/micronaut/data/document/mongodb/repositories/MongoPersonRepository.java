package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.repositories.PersonRepository;
import io.micronaut.data.mongodb.annotation.MongoRepository;

@MongoRepository
public interface MongoPersonRepository extends PersonRepository {
}
