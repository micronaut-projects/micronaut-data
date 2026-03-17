package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@MicronautTest(transactional = false)
@Requires(env="oracle")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "h2")
class OracleAccountSpec extends example.AccountSpec {
    public OracleAccountSpec(AccountRepository accountRepository) {
        super(accountRepository);
    }
}
