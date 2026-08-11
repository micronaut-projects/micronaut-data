package io.micronaut.data.jdbc.oraclexe.bool.legacy;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Used to test Oracle native BOOLEAN SQL generation (requires Oracle 23.1+) against Oracle 21,
 * which should trigger a warning log message and cause the query to fail.
 */
@JdbcRepository(dialect = Dialect.ORACLE, version = "23.1")
public interface Oracle21NativeBooleanRepository extends CrudRepository<LegacyOracleBooleanEntity, Long> {

    List<LegacyOracleBooleanEntity> findByActiveTrue();
}
