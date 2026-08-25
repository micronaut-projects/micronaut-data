package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.VersionedRecord;
import io.micronaut.data.repository.CrudRepository;

@NitriteRepository
public interface VersionedRecordRepository extends CrudRepository<VersionedRecord, String> {
}
