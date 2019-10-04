package io.micronaut.data.jdbc.mysql

import io.micronaut.data.annotation.Id
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.Sale

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlSaleRepository extends CrudRepository<Sale, Long> {
    void updateData(@Id Long id, Map<String, String> data);
}
