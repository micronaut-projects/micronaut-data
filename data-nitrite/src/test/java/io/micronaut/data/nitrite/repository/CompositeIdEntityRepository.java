package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Insert;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeIdEntity;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@NitriteRepository
public interface CompositeIdEntityRepository extends PageableRepository<CompositeIdEntity, CompositeIdEntity> {

    Optional<CompositeIdEntity> findByTenantIdAndRefId(String tenantId, String refId);

    @Insert
    CompositeIdEntity insertOne(CompositeIdEntity entity);
}
