package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.LargeEntity;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface LargeEntityRepository extends CrudRepository<LargeEntity, String> {
}
