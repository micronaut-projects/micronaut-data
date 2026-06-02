package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Person;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

@NitriteRepository
public interface RegressionRepository extends CrudRepository<Person, String> {
    @Query("{\"name\": \":name\", \"$set\": {\"age\": \":age\"}}")
    int updateAgeJson(String name, int age);

    Optional<Person> findByName(String name);

    void update(@io.micronaut.data.annotation.Id String id, String name);

    @Query("{\"$and\": [{\"name\": {\"$eq\": \":name\"}}, {\"age\": {\"$gt\": \":minAge\"}}]}")
    java.util.List<Person> findByNameAndAgeGreaterThanJson(String name, int minAge);
}
