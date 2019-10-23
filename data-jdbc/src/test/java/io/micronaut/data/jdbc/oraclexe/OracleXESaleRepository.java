package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Sale;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleXESaleRepository extends CrudRepository<Sale, Long> {
}
