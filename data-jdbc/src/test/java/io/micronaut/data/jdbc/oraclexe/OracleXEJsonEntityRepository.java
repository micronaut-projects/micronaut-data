package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.JsonEntityRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEJsonEntityRepository extends JsonEntityRepository {

    @Query("SELECT JSON_VALUE(JSON_BLOB, '$.description') FROM JSON_ENTITY WHERE id = :id")
    Optional<String> getDescriptionFromJsonBlob(Long id);

    @Query("SELECT JSON_VALUE(JSON_STRING, '$.description') FROM JSON_ENTITY WHERE id = :id")
    Optional<String> getDescriptionFromJsonString(Long id);

    @Query("SELECT JSON_VALUE(JSON_DEFAULT, '$.description') FROM JSON_ENTITY WHERE id = :id")
    Optional<String> getDescriptionFromJsonDefault(Long id);
}
