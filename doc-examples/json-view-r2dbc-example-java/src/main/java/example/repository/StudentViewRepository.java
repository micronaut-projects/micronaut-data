package example.repository;

import example.domain.view.StudentView;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.GenericRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface StudentViewRepository extends GenericRepository<StudentView, Long> {

    @Query("SELECT * FROM student_schedule")
    @QueryResult(type = QueryResult.Type.JSON)
    Flux<StudentView> findAll();

    @Query("SELECT ss.* FROM student_schedule ss WHERE ss.DATA.studentId=:studentId")
    @QueryResult(type = QueryResult.Type.JSON)
    Mono<StudentView> findByStudentId(Long studentId);
}
