package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CriteriaPerson;
import io.micronaut.data.nitrite.model.CriteriaPersonDto;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.List;

/**
 * Demonstrates Criteria API interaction for Nitrite-backed entities.
 */
@NitriteRepository
public interface CriteriaPersonRepository
    extends CrudRepository<CriteriaPerson, String>, JpaSpecificationExecutor<CriteriaPerson> {

    @Query("{}")
    List<CriteriaPerson> findAllViaQuery();

    List<CriteriaPersonDto> findByName(String name);

    Integer findMaxAgeByName(String name);

    Double findAvgAgeByName(String name);
}
