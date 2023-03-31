package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface H2JsonEntityRepository extends JsonEntityRepository {

    @Query("UPDATE json_entity SET json_blob = :jsonBlob FORMAT JSON WHERE id = :id")
    @Override
    void updateJsonBlobById(Long id, @JsonRepresentation(type = JsonDataType.BLOB) SampleData jsonBlob);
}
