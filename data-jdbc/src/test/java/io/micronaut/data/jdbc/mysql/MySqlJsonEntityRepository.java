package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlJsonEntityRepository extends JsonEntityRepository {
    @Query("UPDATE json_entity SET json_blob = CONVERT(:jsonBlob USING UTF8MB4) WHERE id = :id")
    @Override
    void updateJsonBlobById(Long id, @JsonRepresentation(type = JsonDataType.BLOB) SampleData jsonBlob);
}
