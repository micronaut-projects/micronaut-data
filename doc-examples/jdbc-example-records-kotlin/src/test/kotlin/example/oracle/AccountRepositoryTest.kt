package example.oracle

import example.Account
import example.AccountRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class AccountRepositoryTest {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Test
    fun testReservationUpdate() {
        var account = accountRepository.save(Account(null, "Current", 100L))
        assertNotNull(account.id)

        assertEquals(1, accountRepository.reserveIncrementBalance(account.id!!, 25L))

        account = accountRepository.findById(account.id!!).orElseThrow()
        assertEquals(125L, account.balance)
    }
}
