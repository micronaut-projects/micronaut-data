package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Author;

public interface AuthorRepository extends CrudRepository<Author, Long> {

    Author findByName(String name);

    Author findByBooksTitle(String title);

    long countByNameContains(String text);

    Author findByNameStartsWith(String name);

    Author findByNameEndsWith(String name);

    Author findByNameIgnoreCase(String name);
}