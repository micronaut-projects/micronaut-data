package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.repositories.ExampleEntityRepository;

@R2dbcRepository(dialect = Dialect.H2)
public interface H2ExampleEntityRepository extends ExampleEntityRepository {
}
