package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteMtoRefA;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@NitriteRepository
public interface NitriteMtoRefARepository extends CrudRepository<NitriteMtoRefA, String>, PageableRepository<NitriteMtoRefA, String> {

    @Join(value = "refB")
    @Join(value = "refB.refC")
    @Override
    Optional<NitriteMtoRefA> findById(String id);
}
