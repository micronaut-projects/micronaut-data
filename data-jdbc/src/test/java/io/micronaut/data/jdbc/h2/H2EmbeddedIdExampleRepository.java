package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.embedded.EmbeddedIdExample;
import io.micronaut.data.jdbc.embedded.EmbeddedIdExampleId;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface H2EmbeddedIdExampleRepository extends CrudRepository<EmbeddedIdExample, EmbeddedIdExampleId> {
}
