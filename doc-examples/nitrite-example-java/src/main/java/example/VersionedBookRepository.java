package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

// tag::versioned-repository[]
@NitriteRepository
public interface VersionedBookRepository extends CrudRepository<VersionedBook, String> {

    // Partial update with version check
    void updateTitle(@Id String id, String title, Long version);

    // Partial delete with version check
    void delete(@Id String id, Long version);
}
// end::versioned-repository[]
