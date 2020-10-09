package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.repositories.CatalogRepository;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlCatalogRepository extends CatalogRepository {
}
