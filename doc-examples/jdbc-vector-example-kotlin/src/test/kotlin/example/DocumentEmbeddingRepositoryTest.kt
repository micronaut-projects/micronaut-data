package example

import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class DocumentEmbeddingRepositoryTest {

    @Inject
    lateinit var repository: DocumentEmbeddingRepository

    @Test
    fun saveAndFetchEmbedding() {
        repository.deleteAll()

        val vec: Vector = Vector.of(0.1, 0.2, 0.3)

        repository.save(1L, vec)
        repository.save(2L, Vector.of(0.15, 0.2, 0.25))
        repository.save(3L, Vector.of(0.9, 0.1, 0.1))

        val reloaded = repository.findById(1L).orElse(null)
        assertNotNull(reloaded)
        assertEquals(1L, reloaded!!.id)
        assertEquals(Double::class.javaPrimitiveType, reloaded.embedding.type)
        assertArrayEquals(doubleArrayOf(0.1, 0.2, 0.3), reloaded.embedding.toDoubleArray())

        val near = repository.findTop2ByEmbeddingNear(vec, 2.0)
        assertEquals(2, near.size)
        assertTrue(near.any { it.id == 1L })

        val scored = repository.searchByEmbeddingNear(vec, Score(2.0), ScoringFunction.COSINE)
        assertTrue(scored.results().isNotEmpty())
    }
}
