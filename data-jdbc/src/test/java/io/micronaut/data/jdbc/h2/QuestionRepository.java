package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Question;
import io.micronaut.data.tck.entities.QuestionId;

@JdbcRepository(dialect = Dialect.H2)
public interface QuestionRepository extends CrudRepository<Question, QuestionId> {
}
