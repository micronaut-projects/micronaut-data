package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface AuthorRepository extends CrudRepository<Author, Long> {

    Author findByName(String name);

    Author findByBooksTitle(String title);
}
