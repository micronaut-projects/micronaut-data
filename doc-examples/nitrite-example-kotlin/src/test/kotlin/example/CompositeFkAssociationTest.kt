package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class CompositeFkAssociationTest {

    @Inject
    lateinit var parentRepository: CompositeFkParentRepository

    @Inject
    lateinit var childRepository: CompositeFkChildRepository

    @AfterEach
    fun cleanup() {
        childRepository.deleteAll()
        parentRepository.deleteAll()
    }

    // tag::composite-fk-usage[]
    @Test
    fun testCompositeForeignKeyJoin() {
        val parent = parentRepository.save(CompositeFkParent("tenant-a", 42L))
        childRepository.save(CompositeFkChild("child-a", parent))

        val child = childRepository.findByName("child-a").orElseThrow()

        assertEquals("tenant-a", child.parent?.tenantId)
        assertEquals(42L, child.parent?.refId)
    }
    // end::composite-fk-usage[]
}
