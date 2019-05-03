package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Repository
public interface PersonCrudRepository extends CrudRepository<Person, Long> {

    List<Person> list(Pageable pageable);

    int count(String name);
}
