package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class IndexedBookRepositoryTest {

    @Inject
    IndexedBookRepository repository;

    private final GeometryFactory factory = new GeometryFactory();

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void testSpatialIndexCreation() {
        // Create a book with spatial location
        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128));

        IndexedBook book = new IndexedBook("NYC Guide", 100);
        book.setLocation(nyc);
        book.setDescription("A guide to New York City attractions and landmarks");

        repository.save(book);

        assertNotNull(book.getId());
        assertNotNull(book.getLocation());
    }

    @Test
    void testNearQuery() {
        // Create books at different locations
        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128));
        Point boston = factory.createPoint(new Coordinate(-71.0589, 42.3601));
        Point philadelphia = factory.createPoint(new Coordinate(-75.1652, 39.9526));

        IndexedBook book1 = new IndexedBook("NYC Guide", 100);
        book1.setLocation(nyc);
        book1.setDescription("New York City guide");

        IndexedBook book2 = new IndexedBook("Boston Guide", 80);
        book2.setLocation(boston);
        book2.setDescription("Boston travel guide");

        IndexedBook book3 = new IndexedBook("Philadelphia Guide", 60);
        book3.setLocation(philadelphia);
        book3.setDescription("Philadelphia history");

        repository.saveAll(List.of(book1, book2, book3));

        // Find books near NYC (within 100km = 100000 meters)
        List<IndexedBook> nearResults = repository.findByLocationNear(nyc, 100000.0);

        // Should find NYC book and possibly Philadelphia (within 100km)
        assertFalse(nearResults.isEmpty());
        assertTrue(nearResults.stream().anyMatch(b -> b.getTitle().equals("NYC Guide")));
    }

    @Test
    void testWithinQuery() {
        // Create a bounding box around NYC area
        Polygon nycBox = factory.createPolygon(new Coordinate[] {
            new Coordinate(-74.5, 40.5),  // Southwest
            new Coordinate(-73.5, 40.5),  // Southeast
            new Coordinate(-73.5, 41.0),  // Northeast
            new Coordinate(-74.5, 41.0),  // Northwest
            new Coordinate(-74.5, 40.5)   // Close ring
        });

        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128));
        Point boston = factory.createPoint(new Coordinate(-71.0589, 42.3601));

        IndexedBook book1 = new IndexedBook("NYC Guide", 100);
        book1.setLocation(nyc);

        IndexedBook book2 = new IndexedBook("Boston Guide", 80);
        book2.setLocation(boston);

        repository.saveAll(List.of(book1, book2));

        // Find books within the bounding box
        List<IndexedBook> withinResults = repository.findByLocationWithin(nycBox);

        assertEquals(1, withinResults.size());
        assertEquals("NYC Guide", withinResults.get(0).getTitle());
    }

    @Test
    void testIntersectsQuery() {
        // Create a line that crosses NYC
        LineString line = factory.createLineString(new Coordinate[] {
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 41.0)
        });

        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128));
        Point boston = factory.createPoint(new Coordinate(-71.0589, 42.3601));

        IndexedBook book1 = new IndexedBook("NYC Guide", 100);
        book1.setLocation(nyc);

        IndexedBook book2 = new IndexedBook("Boston Guide", 80);
        book2.setLocation(boston);

        repository.saveAll(List.of(book1, book2));

        // Find books whose location intersects the line
        List<IndexedBook> intersectsResults = repository.findByLocationIntersects(line);

        assertFalse(intersectsResults.isEmpty());
        assertTrue(intersectsResults.stream().anyMatch(b -> b.getTitle().equals("NYC Guide")));
    }

    @Test
    void testFullTextSearch() {
        // Create books with descriptions
        IndexedBook book1 = new IndexedBook("NYC Guide", 100);
        book1.setDescription("A comprehensive guide to New York City attractions and restaurants");

        IndexedBook book2 = new IndexedBook("Boston Travel", 80);
        book2.setDescription("Explore Boston's historic sites and museums");

        IndexedBook book3 = new IndexedBook("Food Guide", 60);
        book3.setDescription("Best restaurants in major American cities");

        repository.saveAll(List.of(book1, book2, book3));

        // Search for books mentioning "New York"
        List<IndexedBook> textResults = repository.searchByDescription("New York");

        assertFalse(textResults.isEmpty());
        assertTrue(textResults.stream().anyMatch(b -> b.getTitle().equals("NYC Guide")));
    }

    @Test
    void testCompoundIndexUsage() {
        // Create books with same title but different pages
        IndexedBook book1 = new IndexedBook("Guide", 100);
        book1.setDescription("Short guide");

        IndexedBook book2 = new IndexedBook("Guide", 500);
        book2.setDescription("Comprehensive guide");

        repository.saveAll(List.of(book1, book2));

        // Both books should be findable
        List<IndexedBook> allBooks = repository.findAll();
        assertEquals(2, allBooks.size());
    }
}
