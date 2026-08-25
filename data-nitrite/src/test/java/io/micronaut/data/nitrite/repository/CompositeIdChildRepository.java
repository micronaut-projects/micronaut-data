package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeIdChild;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface CompositeIdChildRepository extends CrudRepository<CompositeIdChild, String> {

    @Join("parent")
    Optional<CompositeIdChild> findByName(String name);
}
