
package example;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

@MongoRepository
public interface CarManufacturer2Repository extends CrudRepository<CarManufacturer2, Long> {
}
