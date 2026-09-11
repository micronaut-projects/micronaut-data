package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeFkParent;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface CompositeFkParentRepository extends CrudRepository<CompositeFkParent, String> {
}
