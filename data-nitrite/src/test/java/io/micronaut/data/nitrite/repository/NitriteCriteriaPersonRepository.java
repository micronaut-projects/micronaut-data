package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteMpPerson;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

@NitriteRepository
public interface NitriteCriteriaPersonRepository extends CrudRepository<NitriteMpPerson, String>, JpaSpecificationExecutor<NitriteMpPerson> {
}
