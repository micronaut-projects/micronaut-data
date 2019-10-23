package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.data.jdbc.BasicTypes;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEBasicTypesRepository extends CrudRepository<BasicTypes, Long> {
}
