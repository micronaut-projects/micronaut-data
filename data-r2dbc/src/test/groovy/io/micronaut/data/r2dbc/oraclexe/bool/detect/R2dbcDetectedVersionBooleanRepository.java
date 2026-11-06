package io.micronaut.data.r2dbc.oraclexe.bool.detect;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Compiled without a target version, as an application that only opts in to version detection at runtime.
 */
@R2dbcRepository(dialect = Dialect.ORACLE)
public interface R2dbcDetectedVersionBooleanRepository extends CrudRepository<R2dbcDetectedVersionBooleanEntity, Long> {

    List<R2dbcDetectedVersionBooleanEntity> findByActive(Boolean active);
}
