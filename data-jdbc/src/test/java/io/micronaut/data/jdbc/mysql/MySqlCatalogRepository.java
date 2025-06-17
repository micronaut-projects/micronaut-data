package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.jdbc.entities.Catalog;
import io.micronaut.data.tck.repositories.CatalogRepository;

import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlCatalogRepository extends CatalogRepository {

    @Override
    @Join(value = "parent", type = Join.Type.LEFT_FETCH)
    Optional<Catalog> findById(UUID id);
}
