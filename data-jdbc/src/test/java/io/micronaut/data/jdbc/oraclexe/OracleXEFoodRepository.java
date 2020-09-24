package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.FoodRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEFoodRepository extends FoodRepository {
}
