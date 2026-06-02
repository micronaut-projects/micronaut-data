package example;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

// tag::geometry-types[]
public class GeometryTypesExample {

    public static void main(String[] args) {
        GeometryFactory factory = new GeometryFactory();

        // Create a Point - a single coordinate (x, y)
        // IMPORTANT: JTS uses (longitude, latitude), NOT (latitude, longitude)
        Point nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128));

        // Use the geometry in your entity
        // entity.setLocation(nyc);
    }
}
// end::geometry-types[]
