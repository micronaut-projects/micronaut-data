package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteComplexEntity;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.List;

@NitriteRepository
public interface NitriteComplexEntityRepository extends CrudRepository<NitriteComplexEntity, String>, PageableRepository<NitriteComplexEntity, String>, JpaSpecificationExecutor<NitriteComplexEntity> {
    /**
     * {@code values} is an association whose target has no identity, so a reverse lookup for it
     * cannot be resolved through a sub-query.
     *
     * @param key the key to match
     * @return the matching entities
     */
    List<NitriteComplexEntity> findByValuesKey(String key);
}
