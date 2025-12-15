package io.micronaut.data.r2dbc.oraclexe.jsonview;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.entities.Address;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface AddressRepository extends PageableRepository<Address, Long> {
}
