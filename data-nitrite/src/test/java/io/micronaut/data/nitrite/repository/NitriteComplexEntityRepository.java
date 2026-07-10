package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteComplexEntity;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteComplexEntityRepository extends CrudRepository<NitriteComplexEntity, String>, PageableRepository<NitriteComplexEntity, String> {
}
