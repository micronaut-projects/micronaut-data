
package example

import io.micronaut.data.cosmos.annotation.CosmosRepository
import io.micronaut.data.repository.CrudRepository

@CosmosRepository
abstract class AbstractBookRepository implements CrudRepository<Book, String> {

    abstract List<Book> findByTitle(String title)
}
