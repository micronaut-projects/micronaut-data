package io.micronaut.data.r2dbc.oraclexe.bool.nativebool;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@R2dbcRepository(dialect = Dialect.ORACLE, version = "23.1")
public interface R2dbcNativeOracleBooleanRepository extends CrudRepository<R2dbcNativeOracleBooleanEntity, Long> {

    List<R2dbcNativeOracleBooleanEntity> findByActiveTrue();

    List<R2dbcNativeOracleBooleanEntity> findByActiveFalse();
}
