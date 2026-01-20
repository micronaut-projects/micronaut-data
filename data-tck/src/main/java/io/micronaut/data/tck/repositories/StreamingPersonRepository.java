package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Fetch;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonWithIdAndNameDto;

import java.util.stream.Stream;

public interface StreamingPersonRepository extends CrudRepository<Person, Long> {

    @Fetch(1000)
    Stream<Person> list();

    Stream<Person> queryAll();

    @Query("SELECT id, name FROM person")
    @Fetch(1000)
    Stream<PersonWithIdAndNameDto> listAllDto();

    @Query("SELECT id, name FROM person")
    Stream<PersonWithIdAndNameDto> queryAllDto();
}
