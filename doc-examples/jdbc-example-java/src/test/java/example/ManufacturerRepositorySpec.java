package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "spec.name", value = "ManufacturerRepositorySpec")
public class ManufacturerRepositorySpec {

    private final ManufacturerRepository manufacturerRepository;

    public ManufacturerRepositorySpec(ManufacturerRepository manufacturerRepository) {
        this.manufacturerRepository = manufacturerRepository;
    }

    @Test
    void testMockRepo() {
        Assertions.assertTrue(manufacturerRepository instanceof MockManufacturerRepository);
    }
}
