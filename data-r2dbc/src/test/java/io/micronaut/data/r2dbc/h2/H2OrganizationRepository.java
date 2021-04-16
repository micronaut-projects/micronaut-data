package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;

@R2dbcRepository(dialect = Dialect.H2)
public interface H2OrganizationRepository extends OrganizationRepository {
}
