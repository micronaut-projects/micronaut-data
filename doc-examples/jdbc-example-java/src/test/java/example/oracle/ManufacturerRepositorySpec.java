package example.oracle;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "spec.name", value = "example.oracle.ManufacturerRepositorySpec")
@Requires(env="oracle")
class ManufacturerRepositorySpec {
    @Inject
    ManufacturerRepository manufacturerRepository;

    @Test
    void testMockRepo() {
        Assertions.assertTrue(manufacturerRepository instanceof MockManufacturerRepository);
    }
}
