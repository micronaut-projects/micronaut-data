
package example

import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId

@MongoRepository
abstract class AbstractBookRepository : CrudRepository<Book, ObjectId> {

    abstract fun findByTitle(title: String): List<Book>
}
