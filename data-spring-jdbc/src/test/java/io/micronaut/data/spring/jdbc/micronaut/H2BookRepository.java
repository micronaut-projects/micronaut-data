package io.micronaut.data.spring.jdbc.micronaut;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.AuthorRepository;
import io.micronaut.data.tck.repositories.BookRepository;

@JdbcRepository(dialect = Dialect.H2)
public abstract class H2BookRepository extends BookRepository {

    public H2BookRepository(H2AuthorRepository authorRepository) {
        super(authorRepository);
    }

}
