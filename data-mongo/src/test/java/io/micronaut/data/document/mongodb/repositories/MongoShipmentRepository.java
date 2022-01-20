package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.mongo.annotation.MongoRepository;
import io.micronaut.data.document.tck.repositories.ShipmentRepository;

@MongoRepository
public interface MongoShipmentRepository extends ShipmentRepository {
}
