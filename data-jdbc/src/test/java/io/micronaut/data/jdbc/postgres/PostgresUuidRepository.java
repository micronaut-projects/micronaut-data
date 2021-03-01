package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.UuidEntity;

import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresUuidRepository extends CrudRepository<UuidEntity, UUID> {

}
