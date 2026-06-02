package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon

@MicronautTest(transactional = false)
class IndexedBookRepositoryTest {

    @Inject
    lateinit var indexedBookRepository: IndexedBookRepository

    @AfterEach
    fun cleanup() {
        indexedBookRepository.deleteAll()
    }

    @Test
    fun testSpatialIndexCreation() {
        // Create a book with spatial location
        val factory = GeometryFactory()
        val nyc = factory.createPoint(Coordinate(-74.0060, 40.7128))

        val book = IndexedBook("NYC Guide", 100)
        book.location = nyc
        book.description = "A guide to New York City attractions and landmarks"

        indexedBookRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.location)
    }

    @Test
    fun testNearQuery() {
        val factory = GeometryFactory()

        // Create books at different locations
        val nyc = factory.createPoint(Coordinate(-74.0060, 40.7128))
        val boston = factory.createPoint(Coordinate(-71.0589, 42.3601))
        val philadelphia = factory.createPoint(Coordinate(-75.1652, 39.9526))

        val book1 = IndexedBook("NYC Guide", 100)
        book1.location = nyc
        book1.description = "New York City guide"

        val book2 = IndexedBook("Boston Guide", 80)
        book2.location = boston
        book2.description = "Boston travel guide"

        val book3 = IndexedBook("Philadelphia Guide", 60)
        book3.location = philadelphia
        book3.description = "Philadelphia history"

        indexedBookRepository.saveAll(listOf(book1, book2, book3))

        // Find books near NYC (within 100km = 100000 meters)
        val nearResults = indexedBookRepository.findByLocationNear(nyc, 100000.0)

        // Should find NYC book and possibly Philadelphia (within 100km)
        assertTrue(nearResults.isNotEmpty())
        assertTrue(nearResults.any { it.title == "NYC Guide" })
    }

    @Test
    fun testWithinQuery() {
        val factory = GeometryFactory()

        // Create a bounding box around NYC area
        val nycBox: Polygon = factory.createPolygon(arrayOf(
            Coordinate(-74.5, 40.5),  // Southwest
            Coordinate(-73.5, 40.5),  // Southeast
            Coordinate(-73.5, 41.0),  // Northeast
            Coordinate(-74.5, 41.0),  // Northwest
            Coordinate(-74.5, 40.5)   // Close ring
        ))

        val nyc = factory.createPoint(Coordinate(-74.0060, 40.7128))
        val boston = factory.createPoint(Coordinate(-71.0589, 42.3601))

        val book1 = IndexedBook("NYC Guide", 100)
        book1.location = nyc

        val book2 = IndexedBook("Boston Guide", 80)
        book2.location = boston

        indexedBookRepository.saveAll(listOf(book1, book2))

        // Find books within the bounding box
        val withinResults = indexedBookRepository.findByLocationWithin(nycBox)

        assertEquals(1, withinResults.size)
        assertEquals("NYC Guide", withinResults[0].title)
    }

    @Test
    fun testIntersectsQuery() {
        val factory = GeometryFactory()

        // Create a line that crosses NYC
        val line: LineString = factory.createLineString(arrayOf(
            Coordinate(-74.5, 40.5),
            Coordinate(-73.5, 41.0)
        ))

        val nyc = factory.createPoint(Coordinate(-74.0060, 40.7128))
        val boston = factory.createPoint(Coordinate(-71.0589, 42.3601))

        val book1 = IndexedBook("NYC Guide", 100)
        book1.location = nyc

        val book2 = IndexedBook("Boston Guide", 80)
        book2.location = boston

        indexedBookRepository.saveAll(listOf(book1, book2))

        // Find books whose location intersects the line
        val intersectsResults = indexedBookRepository.findByLocationIntersects(line)

        assertTrue(intersectsResults.isNotEmpty())
        assertTrue(intersectsResults.any { it.title == "NYC Guide" })
    }

    @Test
    fun testFullTextSearch() {
        // Create books with descriptions
        val book1 = IndexedBook("NYC Guide", 100)
        book1.description = "A comprehensive guide to New York City attractions and restaurants"

        val book2 = IndexedBook("Boston Travel", 80)
        book2.description = "Explore Boston's historic sites and museums"

        val book3 = IndexedBook("Food Guide", 60)
        book3.description = "Best restaurants in major American cities"

        indexedBookRepository.saveAll(listOf(book1, book2, book3))

        // Search for books mentioning "New York"
        val textResults = indexedBookRepository.searchByDescription("New York")

        assertTrue(textResults.isNotEmpty())
        assertTrue(textResults.any { it.title == "NYC Guide" })
    }

    @Test
    fun testCompoundIndexUsage() {
        // Create books with same title but different pages
        val book1 = IndexedBook("Guide", 100)
        book1.description = "Short guide"

        val book2 = IndexedBook("Guide", 500)
        book2.description = "Comprehensive guide"

        indexedBookRepository.saveAll(listOf(book1, book2))

        // Both books should be findable
        val allBooks = indexedBookRepository.findAll()
        assertEquals(2, allBooks.size)
    }
}
