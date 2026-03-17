package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@MicronautTest
@Property(name = "spec.name", value = "H2ManufacturerRepositorySpec")
@Requires(env="h2")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "oracle")
public class H2ManufacturerRepositorySpec extends ManufacturerRepositorySpec {
    public H2ManufacturerRepositorySpec(H2ManufacturerRepository manufacturerRepository) {
        super(manufacturerRepository);
    }


    @Test
    @Override
    void testMockRepo() {
        Assertions.assertTrue(manufacturerRepository instanceof H2MockManufacturerRepository);
    }
}
