package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import io.micronaut.data.tck.repositories.RestaurantRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleRestaurantRepository extends RestaurantRepository {

    Restaurant saveReturning(Restaurant restaurant);
}
