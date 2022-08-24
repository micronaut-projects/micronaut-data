package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Application;
import io.micronaut.data.tck.entities.Template;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface ApplicationRepository extends CrudRepository<Application, Long> {

    @Join(value = "template.questions", type = Join.Type.LEFT_FETCH)
    Optional<Template> findTemplateById(Long id);
}
