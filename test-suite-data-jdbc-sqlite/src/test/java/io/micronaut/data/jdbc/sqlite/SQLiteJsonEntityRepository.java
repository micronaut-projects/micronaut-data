package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

@JdbcRepository(dialect = Dialect.SQLITE)
public interface SQLiteJsonEntityRepository extends JsonEntityRepository {

    @Query("UPDATE json_entity SET json_blob = :jsonBlob FORMAT JSON WHERE id = :id")
    @Override
    void updateJsonBlobById(Long id, SampleData jsonBlob);
}
