package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PetRepositorySpec : AbstractNeo4jTest() {

    @Inject
    lateinit var petRepository: PetRepository

    @AfterEach
    fun cleanup() {
        petRepository.deleteAll()
    }

    @Test
    fun testSchemaless() {
        // tag::schemaless[]
        val dino = Pet("Dino")
        dino["goesInPatch"] = true // <1>
        dino["legs"] = 4
        petRepository.save(dino)

        val found = petRepository.findById("Dino").get()
        val goesInPatch = found["goesInPatch"] // <2>
        val inPatch = petRepository.findByGoesInPatch(true) // <3>
        // end::schemaless[]

        assertEquals(true, goesInPatch)
        assertEquals(4L, found["legs"])
        assertEquals(listOf("Dino"), inPatch.map { it.name })
    }
}
