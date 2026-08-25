package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedIdChild;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface MappedIdChildRepository extends CrudRepository<MappedIdChild, String> {
}
