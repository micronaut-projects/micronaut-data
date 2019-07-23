package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.CarRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresCarRepository extends CarRepository {
    void update(@Id Long id, String name);
}
