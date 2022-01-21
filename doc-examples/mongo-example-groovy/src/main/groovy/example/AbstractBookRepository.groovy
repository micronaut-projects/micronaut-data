
package example

import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId

@MongoRepository
abstract class AbstractBookRepository implements CrudRepository<Book, ObjectId> {

    abstract List<Book> findByTitle(String title);
}
