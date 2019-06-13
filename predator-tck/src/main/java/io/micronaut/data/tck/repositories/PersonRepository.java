package io.micronaut.data.tck.repositories;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.entities.Person;

import java.util.List;

public interface PersonRepository extends CrudRepository<Person, Long>, PageableRepository<Person, Long> {


    Person save(String name, int age);

    Person get(Long id);

    void updatePerson(@Id Long id, @Parameter("name") String name);

    List<Person> list(Pageable pageable);

    int count(String name);

    @Nullable
    Person findByName(String name);

    List<Person> findByNameLike(String name);

    int findAgeByName(String name);

    int findMaxAgeByNameLike(String name);

    int findMinAgeByNameLike(String name);

    int getSumAgeByNameLike(String name);

    long getAvgAgeByNameLike(String name);

    List<Integer> readAgeByNameLike(String name);

    List<Person> findByNameLikeOrderByAge(String name);

    List<Person> findByNameLikeOrderByAgeDesc(String name);
}
