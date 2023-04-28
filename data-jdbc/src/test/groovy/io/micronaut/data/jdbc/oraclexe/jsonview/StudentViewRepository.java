package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface StudentViewRepository extends CrudRepository<StudentView, Long> {

    @QueryResult(type = QueryResult.Type.JSON)
    Optional<StudentView> findByStudent(String name);

    @QueryResult(type = QueryResult.Type.JSON)
    @Override
    Optional<StudentView> findById(Long id);

    @Query("UPDATE STUDENT_SCHEDULE ss SET ss.data = :data WHERE ss.DATA.student = :name")
    void updateByStudent(@TypeDef(type = DataType.JSON) StudentView data, String name);

    @Query("UPDATE STUDENT_SCHEDULE ss SET ss.DATA = json_transform(DATA, SET '$.student' = :newName) WHERE ss.DATA.student = :oldName")
    void updateName(String oldName, String newName);
}
