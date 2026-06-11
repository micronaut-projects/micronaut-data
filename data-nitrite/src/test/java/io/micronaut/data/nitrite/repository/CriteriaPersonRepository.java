package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CriteriaPerson;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

/**
 * Demonstrates Criteria API interaction for Nitrite-backed entities.
 */
@NitriteRepository
public interface CriteriaPersonRepository
    extends CrudRepository<CriteriaPerson, String>, JpaSpecificationExecutor<CriteriaPerson> {

    @io.micronaut.data.annotation.Query("{}")
    java.util.List<CriteriaPerson> findAllViaQuery();
}
