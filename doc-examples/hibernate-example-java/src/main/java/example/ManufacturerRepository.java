
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface ManufacturerRepository extends CrudRepository<Manufacturer, Long> {

    Manufacturer save(String name);
}
