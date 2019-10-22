package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class UserRepositorySpec {
    @Inject UserRepository userRepository;

    @Test
    void testSoftDelete() {
        final User joe = new User();
        joe.setName("Joe");
        final User fred = new User();
        fred.setName("Fred");
        final User bob = new User();
        bob.setName("Bob");
        userRepository.saveAll(Arrays.asList(
                fred,
                bob,
                joe
        ));

        userRepository.deleteById(joe.getId());

        assertEquals(2, userRepository.count());
        assertTrue(userRepository.existsById(fred.getId()));
        assertFalse(userRepository.existsById(joe.getId()));

        final List<User> disabled = userRepository.findDisabled();
        assertEquals(1, disabled.size());
        assertEquals("Joe", disabled.iterator().next().getName());
    }
}
