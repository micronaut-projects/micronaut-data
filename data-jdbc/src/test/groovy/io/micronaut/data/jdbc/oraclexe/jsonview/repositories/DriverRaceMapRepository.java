package io.micronaut.data.jdbc.oraclexe.jsonview.repositories;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.DriverRaceMap;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface DriverRaceMapRepository extends CrudRepository<DriverRaceMap, Long> {
}
