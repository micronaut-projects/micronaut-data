package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.CountryRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresCountryRepository extends CountryRepository {

}