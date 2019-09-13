package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.repositories.AuthorRepository;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresAuthorRepository extends AuthorRepository {

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    List<Author> listAll();

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    Author queryByName(String name);

}
