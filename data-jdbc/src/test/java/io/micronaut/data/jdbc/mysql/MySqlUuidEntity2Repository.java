package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlUuidEntity2Repository extends CrudRepository<MySqlUuidEntity2, UUID> {

    void update(@Id UUID id, String name);

    MySqlUuidEntity2 getByIdInList(List<UUID> uuids);

}
