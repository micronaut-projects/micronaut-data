
package example

import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId

@MongoRepository
abstract class AbstractBookRepository : KotlinCrudRepository<Book, ObjectId> {

    abstract fun findByTitle(title: String): List<Book>
}
