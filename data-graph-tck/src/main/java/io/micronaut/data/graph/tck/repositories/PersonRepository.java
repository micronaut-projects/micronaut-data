package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.PathDirection;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.tck.entities.Person;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends CrudRepository<Person, UUID> {

    Optional<Person> findByName(String name);

    List<Person> findByAcquaintancesName(String name);

    List<Person> findByEndorsersName(String name);

    List<Person> findByMentorName(String name);

    @PathLimit(5)
    boolean existsPathByMentorBetweenName(String from, String to);

    @PathLimit(5)
    @PathDirection(Direction.INCOMING)
    boolean hasPathByMentorBetweenName(String from, String to);

    @PathLimit(5)
    long countPathsByMentorBetweenName(String from, String to, @PathDirection Direction direction);
}
