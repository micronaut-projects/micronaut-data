package example.repositories;

import example.domain.Pet;
import io.micronaut.context.BeanContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class PetRepositoryTest {

    @Inject
    PetRepository petRepository;


    @Inject
    BeanContext beanContext;

    @Test
    void testQuery() {
        String query = beanContext.getBeanDefinition(PetRepository.class)
                .getRequiredMethod("findByName", String.class)
                .getAnnotationMetadata().stringValue(Query.class).get();

        System.out.println("query = " + query);
    }


    @Test
    void testRetrievePetAndOwner() {
        Pet dino = petRepository.findByName("Dino").orElse(null);
        assertNotNull(dino);
        assertEquals("Dino", dino.getName());
        assertEquals("Fred", dino.getOwner().getName());
    }
}
