package io.micronaut.data.spring.jdbc.micronaut;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.AuthorRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresAuthorRepository extends AuthorRepository {
}
