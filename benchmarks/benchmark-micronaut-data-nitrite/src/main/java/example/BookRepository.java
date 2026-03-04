package example;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

/**
 * The book repository.
 */
@NitriteRepository
public interface BookRepository extends CrudRepository<Book, String> {
    Book findByTitle(String title);
}
