package io.micronaut.data.jdbc.sqlserver;

import io.micronaut.data.jdbc.BasicTypes;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.SQL_SERVER)
public interface MSBasicTypesRepository extends CrudRepository<BasicTypes, Long> {
}
