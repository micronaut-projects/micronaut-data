package io.micronaut.data.r2dbc.oraclexe.search;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface OracleAsyncBookSearchRepository extends AsyncBookSearchRepository {
}
