package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.MappedNumericCompositeIdEntity;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface MappedNumericCompositeIdEntityRepository
    extends CrudRepository<MappedNumericCompositeIdEntity, MappedNumericCompositeIdEntity> {

    Optional<MappedNumericCompositeIdEntity> findByTenantIdAndSequence(Long tenantId, Integer sequence);
}
