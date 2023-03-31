package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.SampleData;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresJsonEntityRepository extends JsonEntityRepository {

    @Query("UPDATE json_entity SET json_blob = to_json(:jsonBlob::json) WHERE id = :id")
    @Override
    void updateJsonBlobById(Long id, @JsonRepresentation(type = JsonDataType.BLOB) SampleData jsonBlob);
}
