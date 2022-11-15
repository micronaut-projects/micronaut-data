
package example;

import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@CosmosRepository
public abstract class AbstractBookRepository implements CrudRepository<Book, String> {

    public abstract List<Book> findByTitle(String title);
}
