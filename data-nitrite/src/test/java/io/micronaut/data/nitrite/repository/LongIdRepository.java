package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.LongIdEntity;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface LongIdRepository extends CrudRepository<LongIdEntity, Long> {

    /**
     * A derived identity query, which reaches its predicate through the field name rather than
     * through the entity operations, so it exercises the query path's document-key rewrite.
     *
     * @param id the identity
     * @return the entity, if present
     */
    Optional<LongIdEntity> queryById(Long id);
}
