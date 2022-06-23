package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject
import java.nio.charset.StandardCharsets

@MicronautTest(transactional = false)
class AccountSpec extends Specification {
    @Inject AccountRepository accountRepository

    void "test encode password"() {
        given:
        def account = new Account(username: "bob", password: "foo")

        when:
        accountRepository.save account

        then:
        new String(account.password.decodeBase64(), StandardCharsets.UTF_8) == 'foo'
    }
}
