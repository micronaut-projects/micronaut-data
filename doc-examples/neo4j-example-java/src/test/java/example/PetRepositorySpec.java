package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PetRepositorySpec extends AbstractNeo4jTest {

    @Inject
    PetRepository petRepository;

    @AfterEach
    void cleanup() {
        petRepository.deleteAll();
    }

    @Test
    void testSchemaless() {
        // tag::schemaless[]
        Pet dino = new Pet("Dino");
        dino.set("goesInPatch", true); // <1>
        dino.set("legs", 4);
        petRepository.save(dino);

        Pet found = petRepository.findById("Dino").orElseThrow();
        Object goesInPatch = found.get("goesInPatch"); // <2>
        List<Pet> inPatch = petRepository.findByGoesInPatch(true); // <3>
        // end::schemaless[]

        assertEquals(true, goesInPatch);
        assertEquals(4L, found.get("legs"));
        assertEquals(List.of("Dino"), inPatch.stream().map(Pet::getName).toList());
    }

    @Test
    void testDynamicRelationships() {
        // tag::dynamicRelationships[]
        Pet dino = new Pet("Dino");
        dino.getRelated().put("PLAYS_WITH", List.of(new Pet("Hoppy"))); // <1>
        petRepository.save(dino); // <2>

        Pet found = petRepository.findById("Dino").orElseThrow();
        List<Object> playsWith = found.getRelated().get("PLAYS_WITH"); // <3>
        // end::dynamicRelationships[]

        assertEquals(List.of("Hoppy"), playsWith.stream().map(pet -> ((Pet) pet).getName()).toList());
    }
}
