package io.micronaut.data.r2dbc.oraclexe.jsonviewoptions;

import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface ProductViewRepository extends CrudRepository<ProductView, Long> {
}
