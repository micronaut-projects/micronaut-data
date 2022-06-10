
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

@Repository("reactive")
interface ReactiveBookRepository extends ReactorCrudRepository<Book, Long> {
}
