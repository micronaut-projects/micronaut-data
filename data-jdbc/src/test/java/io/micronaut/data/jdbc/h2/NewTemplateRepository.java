package io.micronaut.data.jdbc.h2;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Template;

import javax.validation.constraints.NotNull;
import java.util.Optional;

/**
 * Added prefix New just to differ from the existing TemplateRepository.
 */
@JdbcRepository(dialect = Dialect.H2)
public interface NewTemplateRepository extends CrudRepository<Template, Long> {

    @NonNull
    @Override
    @Join(value = "questions", alias = "q", type = Join.Type.LEFT_FETCH)
    Optional<Template> findById(@NonNull @NotNull Long aLong);
}
