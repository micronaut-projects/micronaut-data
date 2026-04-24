package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.SQLITE)
@Join("subEntityAs")
@Join("subEntityBs")
interface CascadeEntityRepository extends CrudRepository<CascadeEntity, Long> {}
