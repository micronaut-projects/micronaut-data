package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.embedded.HouseEntityRepository;

@JdbcRepository(dialect = Dialect.ANSI)
public interface SQLiteHouseEntityRepository extends HouseEntityRepository {
}
