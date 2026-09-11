package example;

import io.micronaut.context.ApplicationContext;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

// tag::near-usage[]
public class SpatialExample {

    public static void main(String[] args) {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            IndexedBookRepository repository = ctx.getBean(IndexedBookRepository.class);
            GeometryFactory factory = new GeometryFactory();

            // Create a point for Maine, USA (longitude, latitude order!)
            // IMPORTANT: JTS uses (x, y) = (longitude, latitude), NOT (latitude, longitude)
            Geometry maine = factory.createPoint(new Coordinate(-69.0, 45.0));

            // Find books within 100km of Maine
            List<IndexedBook> nearResults = repository.findByLocationNear(maine, 100000);
            System.out.println("Near results: " + nearResults.size());
        }
    }
}
// end::near-usage[]

// tag::within-usage[]
class WithinExample {

    public static void main(String[] args) {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            IndexedBookRepository repository = ctx.getBean(IndexedBookRepository.class);
            GeometryFactory factory = new GeometryFactory();

            // Create a bounding box (polygon) around Maine
            // Coordinates must be in (longitude, latitude) order
            Polygon maineBox = factory.createPolygon(new Coordinate[] {
                new Coordinate(-71.0, 43.0),  // Southwest corner
                new Coordinate(-67.0, 43.0),  // Southeast corner
                new Coordinate(-67.0, 47.0),  // Northeast corner
                new Coordinate(-71.0, 47.0),  // Northwest corner
                new Coordinate(-71.0, 43.0)   // Close the ring
            });

            // Find books within the bounding box
            List<IndexedBook> withinResults = repository.findByLocationWithin(maineBox);
            System.out.println("Within results: " + withinResults.size());
        }
    }
}
// end::within-usage[]

// tag::intersects-usage[]
class IntersectsExample {

    public static void main(String[] args) {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            IndexedBookRepository repository = ctx.getBean(IndexedBookRepository.class);
            GeometryFactory factory = new GeometryFactory();

            // Create a line that crosses Maine
            // Coordinates must be in (longitude, latitude) order
            LineString line = factory.createLineString(new Coordinate[] {
                new Coordinate(-70.0, 44.0),
                new Coordinate(-68.0, 46.0)
            });

            // Find books whose location intersects the line
            List<IndexedBook> intersectsResults = repository.findByLocationIntersects(line);
            System.out.println("Intersects results: " + intersectsResults.size());
        }
    }
}
// end::intersects-usage[]
