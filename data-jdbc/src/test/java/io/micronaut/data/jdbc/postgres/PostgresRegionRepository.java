package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.RegionRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresRegionRepository extends RegionRepository{

}