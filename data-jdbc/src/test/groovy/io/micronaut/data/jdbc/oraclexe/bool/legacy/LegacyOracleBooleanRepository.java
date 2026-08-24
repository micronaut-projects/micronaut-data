package io.micronaut.data.jdbc.oraclexe.bool.legacy;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface LegacyOracleBooleanRepository extends CrudRepository<LegacyOracleBooleanEntity, Long> {

    List<LegacyOracleBooleanEntity> findByActiveTrue();

    List<LegacyOracleBooleanEntity> findByActiveFalse();
}
