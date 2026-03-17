
package example;

import io.micronaut.data.repository.GenericRepository;

public interface ManufacturerRepository extends GenericRepository<Manufacturer, Long> {
    Manufacturer findByName(String name);

    Manufacturer save(String name);

    void deleteAll();
}
