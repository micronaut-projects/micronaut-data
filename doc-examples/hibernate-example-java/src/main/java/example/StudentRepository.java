
package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.repository.CrudRepository;

// tag::studentRepository[]
@Repository
public interface StudentRepository extends CrudRepository<Student, Long> {

    void update(@Id Long id, @Version Long version, String name);

    void delete(@Id Long id, @Version Long version);
}
// end::studentRepository[]

