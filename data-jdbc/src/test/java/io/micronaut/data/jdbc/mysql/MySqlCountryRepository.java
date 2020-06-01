package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.tck.repositories.CountryRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlCountryRepository extends CountryRepository {
}
