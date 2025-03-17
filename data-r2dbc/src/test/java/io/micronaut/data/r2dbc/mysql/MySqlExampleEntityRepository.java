package io.micronaut.data.r2dbc.mysql;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.repositories.ExampleEntityRepository;

@R2dbcRepository(dialect = Dialect.MYSQL)
public interface MySqlExampleEntityRepository extends ExampleEntityRepository {
}
