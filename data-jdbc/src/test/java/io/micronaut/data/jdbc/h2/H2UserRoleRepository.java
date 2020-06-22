package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.UserRoleRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface H2UserRoleRepository extends UserRoleRepository {
}
