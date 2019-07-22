package io.micronaut.data.jdbc.oracle;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.NoseRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleNoseRepository extends NoseRepository  {
}
