package io.micronaut.data.spring.jdbc.micronaut;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.AuthorRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface H2AuthorRepository extends AuthorRepository {
}
