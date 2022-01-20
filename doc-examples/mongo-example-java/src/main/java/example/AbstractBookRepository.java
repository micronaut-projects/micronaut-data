
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import java.util.List;

@Repository
public abstract class AbstractBookRepository implements CrudRepository<Book, ObjectId> {

    public abstract List<Book> findByTitle(String title);
}
