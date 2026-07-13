package io.micronaut.data.jdbc.oraclexe.etag;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface ETagBookRepository extends CrudRepository<ETagBook, Long> {
}
