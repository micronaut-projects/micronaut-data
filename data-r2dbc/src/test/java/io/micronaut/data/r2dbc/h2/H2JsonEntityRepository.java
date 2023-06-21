package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

@R2dbcRepository(dialect = Dialect.H2)
public interface H2JsonEntityRepository extends JsonEntityRepository {

    @Query("UPDATE json_entity SET json_blob = :jsonBlob FORMAT JSON WHERE id = :id")
    @Override
    void updateJsonBlobById(Long id, SampleData jsonBlob);
}
