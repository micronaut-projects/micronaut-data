package example.repository;

import example.domain.view.StudentView;
import io.micronaut.data.annotation.JsonDualityView;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface StudentViewRepository extends GenericRepository<StudentView, Long> {

    @Query("SELECT * FROM student_schedule")
    List<StudentView> findAll();

    @Query("SELECT ss.* FROM student_schedule ss WHERE ss.DATA.studentId=:studentId")
    Optional<StudentView> findByStudentId(Long studentId);
}
