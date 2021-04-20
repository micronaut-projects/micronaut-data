package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface StudentRepository extends CrudRepository<Student, Long> {

    @Join("courses")
    @Override
    Optional<Student> findById(@NonNull Long id);

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratings", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratings.course", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratings.student", type = Join.Type.LEFT_FETCH)
    Optional<Student> queryById(Long aLong);
}
