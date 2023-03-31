package io.micronaut.data.r2dbc.mysql;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

@R2dbcRepository(dialect = Dialect.MYSQL)
public interface MySqlJsonEntityRepository extends JsonEntityRepository {

    @Query("UPDATE json_entity SET json_blob = CONVERT(:jsonBlob USING UTF8MB4) WHERE id = :id")
    @Override
    void updateJsonBlobById(Long id, SampleData jsonBlob);
}
