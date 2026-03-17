package example;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
@Replaces(bean = OracleManufacturerRepository.class)
@Requires(property = "spec.name", value = "OracleManufacturerRepositorySpec")
@Requires(env="oracle")
public class OracleMockManufacturerRepository implements OracleManufacturerRepository {

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
