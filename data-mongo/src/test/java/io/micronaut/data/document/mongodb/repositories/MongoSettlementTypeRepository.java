package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.mongo.annotation.MongoRepository;
import io.micronaut.data.document.tck.repositories.SettlementTypeRepository;

@MongoRepository
public interface MongoSettlementTypeRepository extends SettlementTypeRepository {
}
