package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

// tag::versioned-repository-declaration[]
@NitriteRepository
public interface VersionedBookRepository extends CrudRepository<VersionedBook, String> {
// end::versioned-repository-declaration[]

// tag::versioned-repository[]
    // Partial update with version check
    void updateTitle(@Id String id, String title, Long version);

    // Partial delete with version check
    void delete(@Id String id, Long version);
}
// end::versioned-repository[]
