package io.micronaut.data.jdbc.mysql;
import io.micronaut.data.tck.repositories.AuthorRepository;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlAuthorRepository extends AuthorRepository {
}
