package io.micronaut.data.r2dbc.postgres;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.repositories.UserRoleRepository;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresUserRoleRepository extends UserRoleRepository {
}
