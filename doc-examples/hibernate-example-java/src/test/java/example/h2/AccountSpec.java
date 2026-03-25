package example.h2;

import example.AccountRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest(transactional = false)
class AccountSpec extends example.AccountSpec {
    public AccountSpec(AccountRepository accountRepository) {
        super(accountRepository);
    }
}
