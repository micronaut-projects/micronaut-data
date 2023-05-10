package io.micronaut.data.r2dbc.oraclexe.jsonview;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Contact;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEContactRepository extends CrudRepository<Contact, Long> {
}
