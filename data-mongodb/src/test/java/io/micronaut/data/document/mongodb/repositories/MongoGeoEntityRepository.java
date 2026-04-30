package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.mongodb.entities.GeoEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

@MongoRepository
public interface MongoGeoEntityRepository extends CrudRepository<GeoEntity, String> {
}
