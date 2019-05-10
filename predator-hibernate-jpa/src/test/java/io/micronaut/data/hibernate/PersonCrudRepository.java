package io.micronaut.data.hibernate;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface PersonCrudRepository extends CrudRepository<Person, Long> {

    void updatePerson(@Id Long id, String name);

    List<Person> list(Pageable pageable);

    int count(String name);

    @Nullable Person findByName(String name);

    List<Person> findByNameLike(String name);

    @Query("from Person p where p.name = :n")
    List<Person> listPeople(String n);

    @Query("from Person p where p.name = :n")
    Person queryByName(String n);

    int findAgeByName(String name);

    int findMaxAgeByNameLike(String name);

    int findMinAgeByNameLike(String name);

    int getSumAgeByNameLike(String name);

    long getAvgAgeByNameLike(String name);

    List<Integer> readAgeByNameLike(String name);

    List<Person> findByNameLikeOrderByAge(String name);

    List<Person> findByNameLikeOrderByAgeDesc(String name);
}
