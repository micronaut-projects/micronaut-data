package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.charset.StandardCharsets
import java.util.*

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountTest : PostgresHibernateReactiveProperties {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Test
    fun testEncodePassword() = runBlocking {
        val account = Account(null, "bob", "foo")
        accountRepository.save(account)
        assertEquals(
            "foo",
            String(Base64.getDecoder().decode(account.password), StandardCharsets.UTF_8)
        )
        assertNotNull(account.id)
    }
}
