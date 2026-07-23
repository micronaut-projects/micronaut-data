package example.oracle;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest(transactional = false)
@Requires(env="oracle")
class UpsertSpec extends example.UpsertSpec {
}
