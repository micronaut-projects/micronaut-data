package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

// tag::repository[]
@NitriteRepository
public interface BookRepository extends CrudRepository<Book, String> {
// end::repository[]

    Optional<Book> findByTitle(String title);

    // tag::update[]
    void update(@Id String id, String title);
    // end::update[]

// tag::repository[]
}
// end::repository[]

