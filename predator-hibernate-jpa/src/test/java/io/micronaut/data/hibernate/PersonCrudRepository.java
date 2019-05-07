package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface PersonCrudRepository extends CrudRepository<Person, Long> {

    List<Person> list(Pageable pageable);

    int count(String name);

    Person findByName(String name);

    List<Person> findByNameLike(String name);

    @Query("from Person p where p.name = :n")
    List<Person> listPeople(String n);

    @Query("from Person p where p.name = :n")
    Person queryByName(String n);

    int findAgeByName(String name);

    int findMaxAgeByNameLike(String name);

    int findMinAgeByNameLike(String name);
}
