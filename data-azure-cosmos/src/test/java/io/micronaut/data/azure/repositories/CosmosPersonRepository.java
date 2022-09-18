package io.micronaut.data.azure.repositories;

import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.document.tck.repositories.PersonRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;

import java.time.LocalDate;
import java.util.List;

@CosmosRepository
public interface CosmosPersonRepository extends PersonRepository {

    @Override
    default List<Person> findByNameRegexOrderByAgeDesc(String name) {
        throw new RuntimeException();
    }

    @Override
    default List<Integer> readAgeByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default int findMinAgeByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default Long deleteByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default Slice<Person> queryByNameRegex(String name, Pageable pageable) {
        throw new RuntimeException();
    }

    @Override
    default Page<Person> getByNameRegex(String name, Pageable pageable) {
        throw new RuntimeException();
    }

    @Override
    default int getSumAgeByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default long getAvgAgeByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default List<Person> findByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default List<Person> findAllByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default Page<Person> findAllByNameRegex(String name, Pageable pageable) {
        throw new RuntimeException();
    }

    @Override
    default List<Person> findByNameRegexOrderByAge(String name) {
        throw new RuntimeException();
    }


    @Override
    default List<Person> findByNameRegex(String name, Pageable pageable) {
        throw new RuntimeException();
    }

    @Override
    default int findMaxAgeByNameRegex(String name) {
        throw new RuntimeException();
    }

    @Override
    default LocalDate findMaxDateOfBirthByNameRegex(String name) {
        throw new RuntimeException();
    }


    @Override
    default LocalDate findMinDateOfBirthByNameRegex(String name) {
        throw new RuntimeException();
    }

}
