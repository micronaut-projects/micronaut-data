package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@MicronautTest
@Property(name = "spec.name", value = "OracleManufacturerRepositorySpec")
@Requires(env="oracle")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "h2")
public class OracleManufacturerRepositorySpec extends ManufacturerRepositorySpec {
    public OracleManufacturerRepositorySpec(OracleManufacturerRepository manufacturerRepository) {
        super(manufacturerRepository);
    }

    @Test
    @Override
    void testMockRepo() {
        Assertions.assertTrue(manufacturerRepository instanceof OracleMockManufacturerRepository);
    }
}
