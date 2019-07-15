package example.repositories

import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class PetRepositoryTest(private val petRepository: PetRepository) {

    @Test
    fun testRetrievePetAndOwner() {
        val dino = petRepository.findByName("Dino").orElse(null)
        assertNotNull(dino)
        assertEquals("Dino", dino.name)
        assertEquals("Fred", dino.owner.name)
    }
}
