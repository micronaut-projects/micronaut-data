package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeFkChild;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.Optional;

@NitriteRepository
public interface CompositeFkChildRepository extends CrudRepository<CompositeFkChild, String>, JpaSpecificationExecutor<CompositeFkChild> {

    @Join("parent")
    Optional<CompositeFkChild> findByName(String name);
}
