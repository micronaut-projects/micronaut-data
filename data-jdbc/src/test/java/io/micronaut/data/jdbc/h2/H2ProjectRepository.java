package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.jdbc.entities.ProjectId;
import io.micronaut.data.tck.repositories.ProjectRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface H2ProjectRepository extends ProjectRepository {
    @Override
    void update(ProjectId projectId, String name);
}
