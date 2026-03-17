package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest(transactional = false)
@Requires(env="oracle")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "h2")
class OracleItemRepositorySpec extends ItemRepositorySpec {
}
