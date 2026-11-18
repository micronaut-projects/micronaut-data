package io.micronaut.data.r2dbc.oraclexe.testpilot;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface MeasurementRepository extends ReactorCrudRepository<Measurement, Long> {
}
