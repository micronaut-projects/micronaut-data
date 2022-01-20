
package example;

import io.micronaut.data.mongo.annotation.MongoRepository;
import io.micronaut.data.repository.GenericRepository;
import org.bson.types.ObjectId;

@MongoRepository
public interface ManufacturerRepository extends GenericRepository<Manufacturer, ObjectId> {
    Manufacturer findByName(String name);

    Manufacturer save(String name);
}
