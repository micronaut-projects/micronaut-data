package io.micronaut.data.jdbc.oraclexe.bool.nativebool;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryConfiguration;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
@SqlQueryConfiguration(dialectOptionsCompatibility = "ORACLE_23")
public interface NativeOracleBooleanRepository extends CrudRepository<NativeOracleBooleanEntity, Long> {

    List<NativeOracleBooleanEntity> findByActiveTrue();

    List<NativeOracleBooleanEntity> findByActiveFalse();
}
