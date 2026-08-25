package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeIdCollectionParent;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface CompositeIdCollectionParentRepository
    extends CrudRepository<CompositeIdCollectionParent, CompositeIdCollectionParent> {

    @Join("children")
    Optional<CompositeIdCollectionParent> findByTenantIdAndRefId(String tenantId, String refId);

    /**
     * Loads a parent by a non-identity property, so a parent whose composite identity is only
     * half populated can still be fetched with its join.
     *
     * @param name the parent name
     * @return the parent, if any
     */
    @Join("children")
    Optional<CompositeIdCollectionParent> findByName(String name);
}
