package io.micronaut.data.jdbc.sqlite.jakarta_data.entity;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;

/**
 * A repository that inherits from the built-in BasicRepository and adds no methods.
 */
@JdbcRepository(dialect = Dialect.ANSI)
public interface Boxes extends BasicRepository<Box, String> {
}
