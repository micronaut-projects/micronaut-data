package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.R1Author;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@NitriteRepository
public interface R1AuthorRepository extends CrudRepository<R1Author, String> {
    Optional<R1Author> findByName(String name);
    // G2: reverse ONE_TO_MANY lookup with aliased terminal field (book_title)
    List<R1Author> findByBooksTitle(String title);
}
