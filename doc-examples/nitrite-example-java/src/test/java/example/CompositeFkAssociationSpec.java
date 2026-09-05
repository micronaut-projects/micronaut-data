package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
class CompositeFkAssociationSpec {

    @Inject
    CompositeFkParentRepository parentRepository;

    @Inject
    CompositeFkChildRepository childRepository;

    @AfterEach
    void cleanup() {
        childRepository.deleteAll();
        parentRepository.deleteAll();
    }

    // tag::composite-fk-usage[]
    @Test
    void testCompositeForeignKeyJoin() {
        CompositeFkParent parent = parentRepository.save(new CompositeFkParent("tenant-a", 42L));
        childRepository.save(new CompositeFkChild("child-a", parent));

        CompositeFkChild child = childRepository.findByName("child-a").orElseThrow();

        assertEquals("tenant-a", child.getParent().getTenantId());
        assertEquals(42L, child.getParent().getRefId());
    }
    // end::composite-fk-usage[]
}
