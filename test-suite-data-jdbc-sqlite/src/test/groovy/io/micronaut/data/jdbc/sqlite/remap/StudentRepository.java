package io.micronaut.data.jdbc.sqlite.remap;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.ANSI)
interface StudentRepository extends CrudRepository<Student, StudentId> {
}
