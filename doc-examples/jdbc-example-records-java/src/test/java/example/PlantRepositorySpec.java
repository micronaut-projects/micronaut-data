package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
class PlantRepositorySpec {

    @Inject
    PlantRepository plantRepository;

    @Test
    void testCrud() {
        Plant plant = new Plant(11);
        plant = plantRepository.save(plant);

        assertNotNull(plant.id());
        assertEquals(11, plant.age());
        assertNull(plant.name());

        plant = plantRepository.findById(plant.id()).get();
        assertNotNull(plant.id());
        assertEquals(11, plant.age());
        assertNull(plant.name());
    }
}