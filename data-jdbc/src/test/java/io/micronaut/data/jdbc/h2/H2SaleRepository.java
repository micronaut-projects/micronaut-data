package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Sale;

import java.util.Map;

@JdbcRepository(dialect = Dialect.H2)
public interface H2SaleRepository extends CrudRepository<Sale, Long> {
    void updateData(@Id Long id, Map<String, String> data);
}
