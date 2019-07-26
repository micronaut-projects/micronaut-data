package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.jdbc.BasicTypes;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlBasicTypesRepository extends CrudRepository<BasicTypes, Long> {
}
