
package example;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import java.util.List;

@MongoRepository
public abstract class AbstractBookRepository implements CrudRepository<Book, ObjectId> {

    public abstract List<Book> findByTitle(String title);
}
