package example;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Replaces(bean = ManufacturerRepository.class)
public class MockManufacturerRepository implements ManufacturerRepository {
    Map<String, Manufacturer> map = new HashMap<>();
    @Override
    public Manufacturer findByName(String name) {
        return map.get(name);
    }
}
