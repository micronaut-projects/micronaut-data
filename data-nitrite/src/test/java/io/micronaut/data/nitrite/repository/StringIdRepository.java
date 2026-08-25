package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.StringIdEntity;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface StringIdRepository extends CrudRepository<StringIdEntity, String> {
}
