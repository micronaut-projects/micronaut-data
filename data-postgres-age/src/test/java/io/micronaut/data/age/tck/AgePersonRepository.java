package io.micronaut.data.age.tck;

import io.micronaut.data.age.annotation.AgeRepository;
import io.micronaut.data.graph.tck.entities.Person;
import io.micronaut.data.graph.tck.repositories.PersonQueryRepository;

import java.util.List;

@AgeRepository
public interface AgePersonRepository extends PersonQueryRepository {
    List<Person> findByNameIlike(String pattern);
}
