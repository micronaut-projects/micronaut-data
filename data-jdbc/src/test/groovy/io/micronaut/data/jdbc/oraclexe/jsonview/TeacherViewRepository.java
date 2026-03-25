package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface TeacherViewRepository extends PageableRepository<TeacherView, Long> {
    Optional<TeacherView> findByName(String name);
}
