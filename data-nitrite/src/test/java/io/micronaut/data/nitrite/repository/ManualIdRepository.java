package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.ManualIdEntity;
import io.micronaut.data.repository.CrudRepository;
import java.util.UUID;

@NitriteRepository
public interface ManualIdRepository extends CrudRepository<ManualIdEntity, UUID> {
}
