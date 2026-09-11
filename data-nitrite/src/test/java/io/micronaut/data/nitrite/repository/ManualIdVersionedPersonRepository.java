package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Insert;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.ManualIdVersionedPerson;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NitriteRepository
public interface ManualIdVersionedPersonRepository extends CrudRepository<ManualIdVersionedPerson, UUID> {

    Optional<ManualIdVersionedPerson> findFirstByNameOrderByAgeAsc(String name);

    @Insert
    List<ManualIdVersionedPerson> insertBatch(List<ManualIdVersionedPerson> people);

    @Insert
    ManualIdVersionedPerson insertOne(ManualIdVersionedPerson person);
}
