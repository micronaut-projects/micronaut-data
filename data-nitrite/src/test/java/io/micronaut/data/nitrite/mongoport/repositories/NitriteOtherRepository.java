package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteOtherEntity;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

@NitriteRepository
public interface NitriteOtherRepository extends CrudRepository<NitriteOtherEntity, String>, PageableRepository<NitriteOtherEntity, String> {
}
