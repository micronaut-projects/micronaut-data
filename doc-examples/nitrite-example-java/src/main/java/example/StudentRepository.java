package example;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

// tag::studentRepository[]
@NitriteRepository
public interface StudentRepository extends CrudRepository<Student, String>, JpaSpecificationExecutor<Student> {

    // tag::studentRepository-findByName[]
    Student findByName(String name);
    // end::studentRepository-findByName[]
}
// end::studentRepository[]
