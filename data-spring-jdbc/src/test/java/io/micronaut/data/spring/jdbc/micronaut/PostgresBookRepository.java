package io.micronaut.data.spring.jdbc.micronaut;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.BookRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class PostgresBookRepository extends BookRepository {

    public PostgresBookRepository(PostgresAuthorRepository authorRepository) {
        super(authorRepository);
    }

}
