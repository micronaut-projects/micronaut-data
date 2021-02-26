package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@MicronautTest(transactional = false)
public class AccountSpec {
    private final AccountRepository accountRepository;

    public AccountSpec(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Test
    void testEncodePassword() {
        Account account = new Account();
        account.setUsername("bob");
        account.setPassword("foo");
        accountRepository.save(account);
        Assertions.assertEquals(
                "foo",
                new String(Base64.getDecoder().decode(account.getPassword()), StandardCharsets.UTF_8)
        );
    }
}
