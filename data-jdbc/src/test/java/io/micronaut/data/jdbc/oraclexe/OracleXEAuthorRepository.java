package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.repositories.AuthorRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEAuthorRepository extends AuthorRepository {
    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    Author queryByName(String name);
}
