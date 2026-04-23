package io.micronaut.data.jdbc.sqlite.groovy_static_repo;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.UUID;

@JdbcRepository(dialect = Dialect.ANSI)
interface TestEntityRepository extends MyCrudRepository<GTestEntity, UUID> {
}
