package io.micronaut.data.r2dbc.mysql;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.UUID;

@R2dbcRepository(dialect = Dialect.MYSQL)
public interface MySqlUuidEntityRepository extends CrudRepository<MySqlUuidEntity, UUID> {

    void update(@Id UUID id, UUID id2);

}
