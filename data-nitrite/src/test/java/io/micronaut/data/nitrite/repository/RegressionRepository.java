package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Person;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

@NitriteRepository
public interface RegressionRepository extends CrudRepository<Person, String> {
    @Query("UPDATE person SET age = :age WHERE name = :name")
    int updateAgeByName(String name, int age);

    @Query("{\"name\": \":name\", \"$set\": {\"age\": \":age\"}}")
    int updateAgeJson(String name, int age);

    Optional<Person> findByName(String name);
}
