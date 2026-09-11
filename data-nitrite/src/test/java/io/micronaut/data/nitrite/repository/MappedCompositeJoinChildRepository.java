package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedCompositeJoinChild;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface MappedCompositeJoinChildRepository extends CrudRepository<MappedCompositeJoinChild, String> {

    @Join("parent")
    Optional<MappedCompositeJoinChild> findByName(String name);
}
