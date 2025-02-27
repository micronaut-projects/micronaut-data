package io.micronaut.data.jdbc.h2.remap;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
interface StudentRepository extends CrudRepository<Student, StudentId> {

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    Optional<Student> findById(StudentId id);
}
