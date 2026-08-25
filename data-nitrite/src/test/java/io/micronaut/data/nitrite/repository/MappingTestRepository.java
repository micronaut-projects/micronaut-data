package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappingTestEntity;
import io.micronaut.data.repository.CrudRepository;

/**
 * Repository for {@link MappingTestEntity}, used by dispatch-strategy tests.
 */
@NitriteRepository
public interface MappingTestRepository extends CrudRepository<MappingTestEntity, Long> {
}
