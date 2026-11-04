package example

import io.micronaut.data.model.vector.BinaryVector
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class ImageFingerprintRepositorySpec {

    @Inject
    lateinit var repository: ImageFingerprintRepository

    @Test
    fun searchBinaryVectors() {
        repository.deleteAll()
        // tag::binary_vector[]
        val sunset = BinaryVector(byteArrayOf(0xFF.toByte(), 0x00)) // bits packed 8 per byte
        val sunsetCropped = BinaryVector.of(
            true, true, true, true, true, true, true, true,
            false, false, false, false, false, false, false, true)
        // end::binary_vector[]
        val forest = BinaryVector(byteArrayOf(0x00, 0xFF.toByte()))
        repository.save(ImageFingerprint(name = "sunset", hash = sunset))
        repository.save(ImageFingerprint(name = "sunset-cropped", hash = sunsetCropped))
        repository.save(ImageFingerprint(name = "forest", hash = forest))

        // tag::binary_search[]
        val similar = repository.searchTop3ByHashNear(sunset, 4.0).results()
        // end::binary_search[]
        assertEquals(listOf("sunset", "sunset-cropped"), similar.map { it.entity().name })
        assertEquals(listOf(0.0, 1.0), similar.map { it.score().value() })

        val jaccard = repository.searchTop2ByHashNear(sunset, 1.0, ScoringFunction.JACCARD).results()
        assertEquals("sunset-cropped", jaccard[1].entity().name)
        assertEquals(1.0 / 9.0, jaccard[1].score().value(), 1e-6)
    }
}
