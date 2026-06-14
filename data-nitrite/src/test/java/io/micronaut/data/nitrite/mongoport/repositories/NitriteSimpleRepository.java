package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteSimpleEntity;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteSimpleRepository extends CrudRepository<NitriteSimpleEntity, String>, PageableRepository<NitriteSimpleEntity, String> {
}
