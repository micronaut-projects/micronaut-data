package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Replaces(bean = ManufacturerRepository.class)
@Requires(property = "spec.name", value = "ManufacturerRepositorySpec")
public class MockManufacturerRepository implements ManufacturerRepository {

    Map<String, Manufacturer> map = new HashMap<>();

    @Override
    public Manufacturer findByName(String name) {
        return map.get(name);
    }

    @Override
    public Manufacturer save(String name) {
        Manufacturer manufacturer = new Manufacturer(name);
        map.put(name, manufacturer);
        return manufacturer;
    }
}
