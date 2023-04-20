
package example;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

@MongoRepository
public interface CarManufacturer4Repository extends CrudRepository<CarManufacturer4, Long> {
}
