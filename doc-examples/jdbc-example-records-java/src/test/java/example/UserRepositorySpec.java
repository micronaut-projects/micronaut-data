package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class UserRepositorySpec {

    @Inject
    UserRepository userRepository;

    @Test
    void testCrud() {
        assertNotNull(userRepository);

        User user = new User("Jack", new Address("Parizska", new City("Prague")));
        user = userRepository.save(user);

        assertNotNull(user.id());
        assertNotNull(user.version());
        assertEquals(0, user.version());
        assertNotNull(user.address().id());
        assertNotNull(user.address().city().id());

        userRepository.update(user);

        user = userRepository.findById(user.id()).orElse(null);

        assertNotNull(user);
        assertNotNull(user.id());
        assertEquals(1, user.version());
        assertEquals("Jack", user.name());
        assertNotNull(user.address().id());
        assertEquals("Parizska", user.address().street());
        assertEquals("Prague", user.address().city().name());
    }
}