package example.oracle

import example.Account
import example.AccountRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class AccountRepositorySpec extends Specification {

    @Inject
    AccountRepository accountRepository

    void "test reservation update"() {
        when:
        Account account = accountRepository.save(new Account(null, "Current", 100L))

        then:
        account.id() != null
        accountRepository.reserveIncrementBalance(account.id(), 25L) == 1
        accountRepository.findById(account.id()).orElseThrow().balance() == 125L
    }
}
