package io.micronaut.data.jdbc.mariadb.schema

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

/**
 * Entity validated against a manually created table, kept in its own package so that the schema validation
 * doesn't include the entities of the other MariaDB specs.
 */
@MappedEntity("uuid_maria_schema_entity")
class UuidEntity {

    @Id
    Long id

    UUID uuidField
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface UuidEntityRepository extends CrudRepository<UuidEntity, Long> {
}
