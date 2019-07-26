package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.tck.repositories.AuthorRepository;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresAuthorRepository extends AuthorRepository {
}
