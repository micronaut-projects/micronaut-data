package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.model.VersionedBook;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;

@Repository
public interface VersionedBookRepository extends CrudRepository<VersionedBook, Long> {
}
