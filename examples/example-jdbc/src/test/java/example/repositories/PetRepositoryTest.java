package example.repositories;

import example.domain.Pet;
import io.micronaut.test.annotation.MicronautTest;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@MicronautTest
public class PetRepositoryTest {

    @Inject
    PetRepository petRepository;


    @Test
    void testRetrievePetAndOwner() {
        Pet dino = petRepository.findByName("Dino").orElse(null);
        assertNotNull(dino);
        assertEquals("Dino", dino.getName());
        assertEquals("Fred", dino.getOwner().getName());
    }
}
