package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.ProjectRepository;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlProjectRepository extends ProjectRepository {
}
