package io.micronaut.data.jdbc.oraclexe.jsonview.repositories;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.oraclexe.jsonview.entities.Address;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface AddressRepository extends CrudRepository<Address, Long> {
}
