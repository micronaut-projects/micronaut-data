package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.MealRepository;
import io.micronaut.validation.Validated;

@JdbcRepository(dialect = Dialect.H2)
@Validated
public interface H2MealRepository extends MealRepository {
}
