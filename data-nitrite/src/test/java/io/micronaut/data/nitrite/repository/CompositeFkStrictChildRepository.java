package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeFkStrictChild;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface CompositeFkStrictChildRepository extends CrudRepository<CompositeFkStrictChild, String> {

    @Join("parent")
    Optional<CompositeFkStrictChild> findByName(String name);
}
