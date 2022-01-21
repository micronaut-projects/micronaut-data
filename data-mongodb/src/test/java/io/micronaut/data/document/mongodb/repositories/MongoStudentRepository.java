package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.document.tck.repositories.StudentRepository;

@MongoRepository
public interface MongoStudentRepository extends StudentRepository {
}
