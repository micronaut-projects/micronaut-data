package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface MySqlUuidEntityRepository extends CrudRepository<MySqlUuidEntity, UUID> {

    void update(@Id UUID id, UUID id2);

    MySqlUuidEntity getById3InList(List<UUID> uuids);

}
