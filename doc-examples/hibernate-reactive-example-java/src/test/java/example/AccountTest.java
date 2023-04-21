package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.time.MonthDay;
import java.util.Base64;

@MicronautTest(transactional = false)
@Property(name = "jpa.default.properties.hibernate.connection.db-type", value = "mysql")
@Property(name = "jpa.default.reactive", value = "true")
@Property(name = "test-resources.containers.mysql.image-name", value = "mysql:8.0.30")
public class AccountTest {
    @Inject
    AccountRepository accountRepository;

    @Test
    void testEncodePassword() {
        Account account = new Account();
        account.setUsername("bob");
        account.setPassword("foo");
        account.setPaymentDay(MonthDay.of(3, 5));
        accountRepository.save(account).block();
        Assertions.assertEquals(
                "foo",
                new String(Base64.getDecoder().decode(account.getPassword()), StandardCharsets.UTF_8)
        );
        account = accountRepository.findById(account.getId()).blockOptional().get();
        Assertions.assertNotNull(account.getPaymentDay());
        Assertions.assertEquals(3, account.getPaymentDay().getMonthValue());
        Assertions.assertEquals(5, account.getPaymentDay().getDayOfMonth());
    }

}
