package io.micronaut.data.jdbc.oraclexe.bool.nativebool;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface NativeOracleBooleanRepository extends CrudRepository<NativeOracleBooleanEntity, Long> {

    List<NativeOracleBooleanEntity> findByActiveTrue();

    List<NativeOracleBooleanEntity> findByActiveFalse();

    @Query("SELECT * FROM \"NATIVE_ORACLE_BOOLEAN_ENTITY\" WHERE \"ACTIVE\" = :active")
    List<NativeOracleBooleanEntity> findByActive(@TypeDef(type = DataType.BOOLEAN) Boolean active);
}
