
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@Repository
public interface ManufacturerRepository extends ReactorCrudRepository<Manufacturer, Long> {

    Mono<Manufacturer> save(String name);
}
