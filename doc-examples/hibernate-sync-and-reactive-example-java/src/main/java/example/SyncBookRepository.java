
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

@Repository("sync")
interface SyncBookRepository extends CrudRepository<Book, Long> {

}
