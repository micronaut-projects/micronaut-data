package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface AddressRepository extends CrudRepository<Address, Long> {
}
