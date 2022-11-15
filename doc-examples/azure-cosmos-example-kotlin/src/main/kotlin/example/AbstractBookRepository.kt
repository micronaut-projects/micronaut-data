
package example

import io.micronaut.data.cosmos.annotation.CosmosRepository
import io.micronaut.data.repository.CrudRepository

@CosmosRepository
abstract class AbstractBookRepository : CrudRepository<Book, String> {

    abstract fun findByTitle(title: String): List<Book>
}
