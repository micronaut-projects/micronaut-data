package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteRefA;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@NitriteRepository
public interface NitriteRefARepository extends CrudRepository<NitriteRefA, String>, PageableRepository<NitriteRefA, String> {

    @Join(value = "refB")
    @Join(value = "refB.refC")
    @Override
    Optional<NitriteRefA> findById(String id);
}
