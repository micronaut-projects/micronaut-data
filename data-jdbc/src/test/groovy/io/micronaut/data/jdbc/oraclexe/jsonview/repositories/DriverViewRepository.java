package io.micronaut.data.jdbc.oraclexe.jsonview.repositories;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.DriverView;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface DriverViewRepository extends CrudRepository<DriverView, Long> {
}
