package io.micronaut.data.r2dbc.postgres;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresJsonEntityRepository extends JsonEntityRepository {

    @Query("UPDATE json_entity SET json_blob = to_json(:jsonBlob::json) WHERE id = :id")
    @Override
    void updateJsonBlobById(Long id, SampleData jsonBlob);
}
