package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeFkRequiredChild;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface CompositeFkRequiredChildRepository extends CrudRepository<CompositeFkRequiredChild, String> {

    @Join("parent")
    Optional<CompositeFkRequiredChild> findByName(String name);
}
