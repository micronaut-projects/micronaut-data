package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.repositories.BasicTypesRepository;
import io.micronaut.data.mongo.annotation.MongoRepository;

@MongoRepository
public interface MongoBasicTypesRepository extends BasicTypesRepository {
}
