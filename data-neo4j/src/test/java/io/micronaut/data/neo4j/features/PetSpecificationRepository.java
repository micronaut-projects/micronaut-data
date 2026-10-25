package io.micronaut.data.neo4j.features;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

@Neo4jRepository
public interface PetSpecificationRepository extends GenericRepository<Pet, String>, JpaSpecificationExecutor<Pet> {
}
