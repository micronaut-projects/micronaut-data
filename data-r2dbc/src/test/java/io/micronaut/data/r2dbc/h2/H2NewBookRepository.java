package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;

import javax.transaction.Transactional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Join(value = "authors", type = Join.Type.FETCH)
@R2dbcRepository(dialect = Dialect.H2)
@Transactional
public interface H2NewBookRepository extends PageableRepository<NewBook, Long> {

    default NewBook save (final String title, NewAuthor... authors) {
        return save (title, new HashSet<>(Arrays.asList( authors )));
    }

    default NewBook save (@NotNull final String title, @NotEmpty final Set<NewAuthor> authors) {
        NewBook book = new NewBook();
        book.setTitle(title);
        book.setAuthors(authors);

        return save (book);
    }

    Optional<NewBook> findOne(String title );
}
