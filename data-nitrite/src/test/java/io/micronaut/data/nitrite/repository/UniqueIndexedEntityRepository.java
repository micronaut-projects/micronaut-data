package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.UniqueIndexedEntity;
import io.micronaut.data.repository.CrudRepository;

/**
 * Repository for {@link UniqueIndexedEntity}, used to regression-test concurrent writes against
 * unique and full-text indexes.
 */
@NitriteRepository
public interface UniqueIndexedEntityRepository extends CrudRepository<UniqueIndexedEntity, Long> {
}
