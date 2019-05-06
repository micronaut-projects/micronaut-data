package io.micronaut.data.hibernate;

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

}
