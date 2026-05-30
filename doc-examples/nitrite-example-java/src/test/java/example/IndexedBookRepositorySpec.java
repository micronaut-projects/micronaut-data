package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
class IndexedBookRepositorySpec {

    @Inject
    IndexedBookRepository repository;

    private final GeometryFactory factory = new GeometryFactory();

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void testSpatialQueries() {
        repository.findAll(); // Trigger index creation

        IndexedBook book = new IndexedBook("The Stand", 1000);
        book.setLocation(factory.createPoint(new Coordinate(-69.0, 45.0))); // Maine
        repository.save(book);

        // Near
        List<IndexedBook> nearResults = repository.findByLocationNear(factory.createPoint(new Coordinate(-69.0, 45.0)), 0.1);
        assertEquals(1, nearResults.size());

        // Within
        Polygon maineBox = factory.createPolygon(new Coordinate[] {
                new Coordinate(-71.0, 43.0),
                new Coordinate(-67.0, 43.0),
                new Coordinate(-67.0, 47.0),
                new Coordinate(-71.0, 47.0),
                new Coordinate(-71.0, 43.0)
        });
        List<IndexedBook> withinResults = repository.findByLocationWithin(maineBox);
        assertEquals(1, withinResults.size());

        // Intersects
        LineString line = factory.createLineString(new Coordinate[] {
                new Coordinate(-70.0, 44.0),
                new Coordinate(-68.0, 46.0)
        });
        List<IndexedBook> intersectsResults = repository.findByLocationIntersects(line);
        assertEquals(1, intersectsResults.size());
    }

    @Test
    void testFullTextSearch() {
        repository.findAll(); // Trigger index creation
        IndexedBook book = new IndexedBook("The Stand", 1000);
        book.setDescription("A post-apocalyptic horror/fantasy novel by Stephen King");
        repository.save(book);

        List<IndexedBook> searchResults = repository.searchByDescription("apocalyptic");
        assertEquals(1, searchResults.size());
    }
}
