package io.micronaut.data.jdbc.h2.remap;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.H2)
interface StudentRepository extends CrudRepository<Student, StudentId> {
}
