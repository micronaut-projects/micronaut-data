package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteMpPerson;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

@NitriteRepository
public interface NitriteCriteriaPersonRepository extends CrudRepository<NitriteMpPerson, String>, JpaSpecificationExecutor<NitriteMpPerson> {
}
