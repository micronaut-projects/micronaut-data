package example.oracle;

import example.Account;
import example.AccountRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest(transactional = false)
class AccountRepositorySpec {

    @Inject
    AccountRepository accountRepository;

    @Test
    void testReservationUpdate() {
        Account account = accountRepository.save(new Account(null, "Current", 100L));
        assertNotNull(account.id());

        assertEquals(1, accountRepository.reserveIncrementBalance(account.id(), 25L));

        account = accountRepository.findById(account.id()).orElseThrow();
        assertEquals(125L, account.balance());
    }
}
