package example.repository;

import example.domain.Teacher;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface TeacherRepository extends PageableRepository<Teacher, Long> {
}
