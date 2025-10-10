package io.micronaut.data.jdbc.h2.remap;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.H2)
interface CourseRepository extends CrudRepository<Course, UUID> {

    @Join("students")
    List<Student> findStudentsById(UUID id);
}
