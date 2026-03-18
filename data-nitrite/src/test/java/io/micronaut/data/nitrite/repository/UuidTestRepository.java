package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.UuidTestEntity;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NitriteRepository
public interface UuidTestRepository extends CrudRepository<UuidTestEntity, UUID> {

    @Query("{}")
    List<UuidTestEntity> findAll();

    /**
     * KNOWN BUG: @Query with field filter for UUID strings returns empty.
     * Field name in JSON (canonicalName) doesn't match stored field (canonical_name).
     */
    @Query("{\"canonicalName\": {\"$eq\": :canonicalName}}")
    Optional<UuidTestEntity> findByCanonicalName(String canonicalName);
}
