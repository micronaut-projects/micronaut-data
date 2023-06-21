
package example;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import java.util.List;

@MongoRepository
interface BookRepository extends CrudRepository<Book, ObjectId> {

    @Override
    List<Book> findAll();

}
