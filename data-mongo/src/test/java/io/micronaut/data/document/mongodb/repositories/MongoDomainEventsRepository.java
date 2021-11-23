package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.mongo.annotation.MongoRepository;
import io.micronaut.data.document.tck.repositories.DomainEventsRepository;

@MongoRepository
public interface MongoDomainEventsRepository extends DomainEventsRepository {
}
