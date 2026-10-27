package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.tck.entities.Person;

import java.util.Optional;

/**
 * Fetch joins, for the databases that support them.
 */
public interface PersonQueryRepository extends PersonRepository {

    @Join("mentor")
    Optional<Person> queryByName(String name);

    @PathLimit(5)
    Optional<GraphPath<Person>> findShortestPathByMentorBetweenName(String from, String to);
}
