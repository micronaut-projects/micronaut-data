package example;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
@Replaces(bean = H2ManufacturerRepository.class)
@Requires(property = "spec.name", value = "H2ManufacturerRepositorySpec")
@Requires(notEnv="oracle")
public class H2MockManufacturerRepository implements H2ManufacturerRepository {

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

    @Override
    public void deleteAll() {
        map.clear();
    }
}
