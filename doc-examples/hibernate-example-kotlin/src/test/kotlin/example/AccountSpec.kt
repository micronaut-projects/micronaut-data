package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.*

@MicronautTest(transactional = false)
class AccountSpec(private val accountRepository: AccountRepository) {
    @Test
    fun testEncodePassword() {
        val account = Account(null, "bob", "foo")
        accountRepository.save(account)
        assertEquals(
            "foo",
            String(Base64.getDecoder().decode(account.password), StandardCharsets.UTF_8)
        )
        assertNotNull(account.id)
    }
}