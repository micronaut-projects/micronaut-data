package io.micronaut.data.jdbc.sqlserver;

import io.micronaut.data.tck.repositories.AuthorRepository;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.SQL_SERVER)
public interface MSAuthorRepository extends AuthorRepository {
}
