package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class IndexedBookRepositorySpec {

    @Inject
    IndexedBookRepository repository

    def factory = new GeometryFactory()

    @AfterEach
    void cleanup() {
        repository.deleteAll()
    }

    @Test
    void testSpatialIndexCreation() {
        // Create a book with spatial location
        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))

        IndexedBook book = new IndexedBook("NYC Guide", 100)
        book.location = nyc
        book.description = "A guide to New York City attractions and landmarks"

        repository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.location)
    }

    @Test
    void testNearQuery() {
        // Create books at different locations
        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        Point boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))
        Point philadelphia = factory.createPoint(new Coordinate(-75.1652, 39.9526))

        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.location = nyc
        book1.description = "New York City guide"

        IndexedBook book2 = new IndexedBook("Boston Guide", 80)
        book2.location = boston
        book2.description = "Boston travel guide"

        IndexedBook book3 = new IndexedBook("Philadelphia Guide", 60)
        book3.location = philadelphia
        book3.description = "Philadelphia history"

        repository.saveAll([book1, book2, book3])

        // Find books near NYC (within 100km = 100000 meters)
        def nearResults = repository.findByLocationNear(nyc, 100000.0)

        // Should find NYC book
        assertFalse(nearResults.empty)
        assertTrue(nearResults.any { it.title == "NYC Guide" })
    }

    @Test
    void testWithinQuery() {
        // Create a bounding box around NYC area
        Geometry nycBox = factory.createPolygon([
            new Coordinate(-74.5, 40.5),  // Southwest
            new Coordinate(-73.5, 40.5),  // Southeast
            new Coordinate(-73.5, 41.0),  // Northeast
            new Coordinate(-74.5, 41.0),  // Northwest
            new Coordinate(-74.5, 40.5)   // Close ring
        ] as Coordinate[])

        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        Point boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))

        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.location = nyc

        IndexedBook book2 = new IndexedBook("Boston Guide", 80)
        book2.location = boston

        repository.saveAll([book1, book2])

        // Find books within the bounding box
        def withinResults = repository.findByLocationWithin(nycBox)

        assertEquals(1, withinResults.size())
        assertEquals("NYC Guide", withinResults[0].title)
    }

    @Test
    void testIntersectsQuery() {
        // Create a line that crosses NYC
        Geometry line = factory.createLineString([
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 41.0)
        ] as Coordinate[])

        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        Point boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))

        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.location = nyc

        IndexedBook book2 = new IndexedBook("Boston Guide", 80)
        book2.location = boston

        repository.saveAll([book1, book2])

        // Find books whose location intersects the line
        def intersectsResults = repository.findByLocationIntersects(line)

        assertFalse(intersectsResults.empty)
        assertTrue(intersectsResults.any { it.title == "NYC Guide" })
    }

    @Test
    void testFullTextSearch() {
        // Create books with descriptions
        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.description = "A comprehensive guide to New York City attractions and restaurants"

        IndexedBook book2 = new IndexedBook("Boston Travel", 80)
        book2.description = "Explore Boston's historic sites and museums"

        IndexedBook book3 = new IndexedBook("Food Guide", 60)
        book3.description = "Best restaurants in major American cities"

        repository.saveAll([book1, book2, book3])

        // Search for books mentioning "New York"
        def textResults = repository.searchByDescription("New York")

        assertFalse(textResults.empty)
        assertTrue(textResults.any { it.title == "NYC Guide" })
    }

    @Test
    void testCompoundIndexUsage() {
        // Create books with same title but different pages
        IndexedBook book1 = new IndexedBook("Guide", 100)
        book1.description = "Short guide"

        IndexedBook book2 = new IndexedBook("Guide", 500)
        book2.description = "Comprehensive guide"

        repository.saveAll([book1, book2])

        // Both books should be findable
        def allBooks = repository.findAll()
        assertEquals(2, allBooks.size())
    }
}
