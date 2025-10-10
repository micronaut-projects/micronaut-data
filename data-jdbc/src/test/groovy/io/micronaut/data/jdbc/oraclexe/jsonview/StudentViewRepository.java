package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface StudentViewRepository extends PageableRepository<StudentView, Long> {

    Optional<StudentView> findByName(String name);

    @Query("UPDATE STUDENT_VIEW ss SET ss.data = :data WHERE ss.DATA.name = :name")
    void updateByName(@TypeDef(type = DataType.JSON) StudentView data, String name);

    @Query("UPDATE STUDENT_VIEW ss SET ss.DATA = json_transform(DATA, SET '$.name' = :newName) WHERE ss.DATA.name = :oldName")
    void updateName(String oldName, String newName);

    void updateName(@Id Long id, String name);

    void updateAverageGradeAndName(@Id Long id, Double averageGrade, String name);

    Double findMaxAverageGrade();

    String findNameById(Long id);

    Optional<LocalDateTime> findStartDateTimeById(Long id);

    boolean findActiveById(Long id);

    List<StudentView> findAllByActive(boolean active);

    List<StudentView> findAllOrderByActive();

    String findAddressStreetById(Long id);

    LocalDate findBirthDateById(Long id);

    List<StudentView> findAllOrderByBirthDate();
}
