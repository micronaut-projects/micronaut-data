package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.DatasourceRecord;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository bound to the {@code secondary} datasource.
 */
@NitriteRepository("secondary")
public interface SecondaryDatasourceRepository extends CrudRepository<DatasourceRecord, String> {

    List<DatasourceRecord> findByLabel(String label);
}
