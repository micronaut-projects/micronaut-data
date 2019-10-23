package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.CityRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresCityRepository extends CityRepository {
}
