package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import java.util.Optional;

@Join(value = "books", type = Join.Type.FETCH)
@R2dbcRepository(dialect = Dialect.H2)
@Transactional
public interface H2NewAuthorRepository extends PageableRepository<NewAuthor, Long> {
    NewAuthor save(@NotNull final String name);


    Optional<NewAuthor> findOne(String name );
}
