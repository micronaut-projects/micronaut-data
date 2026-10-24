package io.micronaut.data.jdbc.oraclexe.bool.detect;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Compiled without a target version, as an application that only opts in to version detection at runtime.
 */
@JdbcRepository(dialect = Dialect.ORACLE)
public interface DetectedVersionBooleanRepository extends CrudRepository<DetectedVersionBooleanEntity, Long> {

    List<DetectedVersionBooleanEntity> findByActive(Boolean active);
}
