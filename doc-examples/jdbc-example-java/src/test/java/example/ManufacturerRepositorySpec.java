package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

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
