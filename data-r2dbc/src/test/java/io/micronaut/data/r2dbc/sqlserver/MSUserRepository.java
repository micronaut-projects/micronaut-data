package io.micronaut.data.r2dbc.sqlserver;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.repositories.UserRepository;

@R2dbcRepository(dialect = Dialect.SQL_SERVER)
public interface MSUserRepository extends UserRepository {
}
