package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class UserRepositoryTest {
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
        )).collectList().block();

        userRepository.deleteById(joe.getId()).block();

        assertEquals(2, userRepository.count().block());
        assertTrue(userRepository.existsById(fred.getId()).block());
        assertFalse(userRepository.existsById(joe.getId()).block());

        final List<User> disabled = userRepository.findDisabled().collectList().block();
        assertEquals(1, disabled.size());
        assertEquals("Joe", disabled.iterator().next().getName());
    }
}
