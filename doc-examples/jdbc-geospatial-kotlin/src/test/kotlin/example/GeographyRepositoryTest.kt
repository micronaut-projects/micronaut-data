package example

import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class GeographyRepositoryTest {

    @Inject
    lateinit var geographyEntityJsonRepository: GeographyEntityJsonRepository

    @Inject
    lateinit var geographyEntityWktRepository: GeographyEntityWktRepository

    @Test
    fun testCrudWhenJsonConversionUsed() {
        val entity = GeographyEntityJson(
            point = Point(10.0, 11.0),
            multiPoint = MultiPoint(listOf(Point(21.0, 22.0), Point(23.0, 24.0)))
        )

        val savedEntity = geographyEntityJsonRepository.save(entity)
        assertNotNull(savedEntity.id)
        assertEquals(savedEntity, geographyEntityJsonRepository.findById(savedEntity.id!!).orElse(null))

        val changedEntity = savedEntity.copy(
            point = Point(31.0, 32.0),
            multiPoint = MultiPoint(listOf(Point(41.0, 42.0), Point(43.0, 44.0)))
        )
        geographyEntityJsonRepository.update(changedEntity)
        assertEquals(changedEntity, geographyEntityJsonRepository.findById(savedEntity.id!!).orElse(null))

        val withoutMultiPoint = changedEntity.copy(multiPoint = null)
        geographyEntityJsonRepository.update(withoutMultiPoint)
        assertEquals(withoutMultiPoint, geographyEntityJsonRepository.findById(savedEntity.id!!).orElse(null))
    }

    @Test
    fun testCrudWhenWktConversionUsed() {
        val entity = GeographyEntityWkt(
            point = Point(10.0, 11.0),
            multiPoint = MultiPoint(listOf(Point(21.0, 22.0), Point(23.0, 24.0)))
        )

        val savedEntity = geographyEntityWktRepository.save(entity)
        assertNotNull(savedEntity.id)
        assertEquals(savedEntity, geographyEntityWktRepository.findById(savedEntity.id!!).orElse(null))

        val changedEntity = savedEntity.copy(
            point = Point(31.0, 32.0),
            multiPoint = MultiPoint(listOf(Point(41.0, 42.0), Point(43.0, 44.0)))
        )
        geographyEntityWktRepository.update(changedEntity)
        assertEquals(changedEntity, geographyEntityWktRepository.findById(savedEntity.id!!).orElse(null))

        val withoutMultiPoint = changedEntity.copy(multiPoint = null)
        geographyEntityWktRepository.update(withoutMultiPoint)
        assertEquals(withoutMultiPoint, geographyEntityWktRepository.findById(savedEntity.id!!).orElse(null))
    }
}
