package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@Neo4jRepository
public interface ReviewerRepository extends CrudRepository<Reviewer, String> {

    @Join("trainees")
    Optional<Reviewer> findByFullName(String fullName);

    @Join("trainees")
    @Join("trainees.trainees")
    Optional<Reviewer> findByFullNameAndReviewCount(String fullName, Integer reviewCount);
}
