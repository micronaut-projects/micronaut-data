package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.repositories.BookRepository;

@R2dbcRepository(dialect = Dialect.H2)
@Join(value = "genre", type = Join.Type.LEFT_FETCH)
public abstract class H2BookRepository extends BookRepository {
    public H2BookRepository(H2AuthorRepository authorRepository) {
        super(authorRepository);
    }
}
