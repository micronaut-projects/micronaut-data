package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.DualIdTestEntity;
import io.micronaut.data.repository.CrudRepository;
import java.util.UUID;

@NitriteRepository
public interface DualIdTestRepository extends CrudRepository<DualIdTestEntity, UUID> {
}
