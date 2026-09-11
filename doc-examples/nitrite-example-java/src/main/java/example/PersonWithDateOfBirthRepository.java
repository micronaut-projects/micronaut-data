package example;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

// tag::personWithDateOfBirthRepository[]
@NitriteRepository
public interface PersonWithDateOfBirthRepository extends CrudRepository<PersonWithDateOfBirth, String>, JpaSpecificationExecutor<PersonWithDateOfBirth> {

    // tag::personWithDateOfBirthRepository-countByName[]
    long countByName(String name);
    // end::personWithDateOfBirthRepository-countByName[]
}
// end::personWithDateOfBirthRepository[]
