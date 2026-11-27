package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.tck.entities.Person;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends CrudRepository<Person, UUID> {

    Optional<Person> findByName(String name);

    List<Person> findByMentorName(String name);
}
