
package example;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

@MongoRepository
public interface CarManufacturer1Repository extends CrudRepository<CarManufacturer1, Long> {
}
