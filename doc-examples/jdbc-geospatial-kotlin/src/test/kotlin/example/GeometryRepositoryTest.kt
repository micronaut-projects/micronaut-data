package example

import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class GeometryRepositoryTest {

    @Inject
    lateinit var geometryEntityJsonRepository: GeometryEntityJsonRepository

    @Inject
    lateinit var geometryEntityWktRepository: GeometryEntityWktRepository

    @Test
    fun testCrudWhenJsonConversionUsed() {
        val entity = GeometryEntityJson(
            point = Point(10.0, 11.0),
            multiPoint = MultiPoint(listOf(Point(21.0, 22.0), Point(23.0, 24.0)))
        )

        val savedEntity = geometryEntityJsonRepository.save(entity)
        assertNotNull(savedEntity.id)
        assertEquals(savedEntity, geometryEntityJsonRepository.findById(savedEntity.id!!).orElse(null))

        val changedEntity = savedEntity.copy(
            point = Point(31.0, 32.0),
            multiPoint = MultiPoint(listOf(Point(41.0, 42.0), Point(43.0, 44.0)))
        )
        geometryEntityJsonRepository.update(changedEntity)
        assertEquals(changedEntity, geometryEntityJsonRepository.findById(savedEntity.id!!).orElse(null))

        val withoutMultiPoint = changedEntity.copy(multiPoint = null)
        geometryEntityJsonRepository.update(withoutMultiPoint)
        assertEquals(withoutMultiPoint, geometryEntityJsonRepository.findById(savedEntity.id!!).orElse(null))
    }

    @Test
    fun testCrudWhenWktConversionUsed() {
        val entity = GeometryEntityWkt(
            point = Point(10.0, 11.0),
            multiPoint = MultiPoint(listOf(Point(21.0, 22.0), Point(23.0, 24.0)))
        )

        val savedEntity = geometryEntityWktRepository.save(entity)
        assertNotNull(savedEntity.id)
        assertEquals(savedEntity, geometryEntityWktRepository.findById(savedEntity.id!!).orElse(null))

        val changedEntity = savedEntity.copy(
            point = Point(31.0, 32.0),
            multiPoint = MultiPoint(listOf(Point(41.0, 42.0), Point(43.0, 44.0)))
        )
        geometryEntityWktRepository.update(changedEntity)
        assertEquals(changedEntity, geometryEntityWktRepository.findById(savedEntity.id!!).orElse(null))

        val withoutMultiPoint = changedEntity.copy(multiPoint = null)
        geometryEntityWktRepository.update(withoutMultiPoint)
        assertEquals(withoutMultiPoint, geometryEntityWktRepository.findById(savedEntity.id!!).orElse(null))
    }
}
