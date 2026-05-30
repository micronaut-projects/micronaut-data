package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteOtoParent;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@NitriteRepository
public interface NitriteOtoParentRepository extends CrudRepository<NitriteOtoParent, String>, PageableRepository<NitriteOtoParent, String> {

    @Join(value = "children")
    @Override
    Optional<NitriteOtoParent> findById(String id);
}
