package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

/**
 * The book repository.
 */
@NitriteRepository
public interface BookRepository extends CrudRepository<Book, String> {
    Book findByTitle(String title);

    // Exercises Metadata-Aware Coercion (int type is known from entity)
    List<Book> findByPages(int pages);

    // Exercises Dynamic Query fallback/deduplication
    @Query("{\"pages\": {\"$eq\": :pages}}")
    List<Book> findByPagesQuery(int pages);
}
