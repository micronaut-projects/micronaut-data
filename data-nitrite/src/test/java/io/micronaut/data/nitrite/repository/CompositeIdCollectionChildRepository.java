package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeIdCollectionChild;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface CompositeIdCollectionChildRepository extends CrudRepository<CompositeIdCollectionChild, String> {
}
