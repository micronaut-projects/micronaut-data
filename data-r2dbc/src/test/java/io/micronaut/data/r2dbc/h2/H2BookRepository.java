package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;

import jakarta.transaction.Transactional;
import java.util.Optional;

@R2dbcRepository(dialect = Dialect.H2)
public abstract class H2BookRepository extends BookRepository {
    public H2BookRepository(H2AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public abstract Optional<Book> queryById(Long id);
}
