package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.mongo.annotation.MongoRepository;

@MongoRepository
public interface MongoAuthorRepository extends AuthorRepository {
}
