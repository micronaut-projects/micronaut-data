package example.repository;

import example.domain.StudentClass;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface StudentClassRepository extends PageableRepository<StudentClass, Long> {
}
