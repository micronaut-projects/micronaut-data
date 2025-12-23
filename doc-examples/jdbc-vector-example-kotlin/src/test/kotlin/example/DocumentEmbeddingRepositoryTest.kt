package example

import io.micronaut.data.model.vector.Vector
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class DocumentEmbeddingRepositoryTest {

    @Inject
    lateinit var repository: DocumentEmbeddingRepository

    @Test
    fun saveAndFetchEmbedding() {
        repository.deleteAll()

        val vec: Vector = Vector.of(0.1f, 0.2f, 0.3f)

        // Use custom insert with explicit id to avoid dialect-specific ID generation
        repository.insertEmbedding(1L, vec)

        val reloaded = repository.findById(1L).orElse(null)
        assertNotNull(reloaded)
        assertEquals(1L, reloaded!!.id)
        assertEquals(Double::class.javaPrimitiveType, reloaded.embedding.type)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), reloaded.embedding.toFloatArray())
    }
}
