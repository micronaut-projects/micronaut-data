package example.oracle;

import example.AccountRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest(transactional = false)
@Requires(env="oracle")
class AccountSpec extends example.AccountSpec {
    public AccountSpec(AccountRepository accountRepository) {
        super(accountRepository);
    }
}
