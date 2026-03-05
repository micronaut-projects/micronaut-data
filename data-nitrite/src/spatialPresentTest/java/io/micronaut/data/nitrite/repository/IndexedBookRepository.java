package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.IndexedBook;
import io.micronaut.data.repository.CrudRepository;

/**
 * Repository for {@link IndexedBook} used in index creation tests.
 */
@NitriteRepository
public interface IndexedBookRepository extends CrudRepository<IndexedBook, String> {
}
