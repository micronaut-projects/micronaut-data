package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.IntegerIdEntity;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface IntegerIdEntityRepository extends CrudRepository<IntegerIdEntity, Integer> {
}
