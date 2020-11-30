package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Student;

@Repository
public interface JpaStudentRepository extends CrudRepository<Student, Long> {

    void updateStudentName(@Id Long id, @Version Long version, String name);

    void updateStudentNameNoVersion(@Id Long id, String name);

    void updateById(@Id Long id, @Version Long version, String name);

    void delete(@Id Long id, @Version Long version, String name);

    void delete(@Id Long id, @Version Long version);

    void updateStudentName(@Id Long id, String name);

    void updateById(@Id Long id, String name);

}
