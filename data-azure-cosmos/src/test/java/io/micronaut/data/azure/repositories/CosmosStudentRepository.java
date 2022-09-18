package io.micronaut.data.azure.repositories;

import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.repositories.StudentRepository;

@CosmosRepository
public interface CosmosStudentRepository extends StudentRepository {
}
