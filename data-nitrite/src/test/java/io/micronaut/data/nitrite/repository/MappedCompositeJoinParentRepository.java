package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedCompositeJoinParent;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface MappedCompositeJoinParentRepository extends CrudRepository<MappedCompositeJoinParent, MappedCompositeJoinParent> {
}
