package example

import io.micronaut.context.ApplicationContext
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon

// tag::near-usage[]
class SpatialExample {

    static void main(String[] args) {
        def ctx = ApplicationContext.run()
        ctx.use { applicationContext ->
            def repository = applicationContext.getBean(IndexedBookRepository)
            def factory = new GeometryFactory()

            // Create a point for Maine, USA (longitude, latitude order!)
            // IMPORTANT: JTS uses (x, y) = (longitude, latitude), NOT (latitude, longitude)
            Geometry maine = factory.createPoint(new Coordinate(-69.0, 45.0))

            // Find books within 100km of Maine
            def nearResults = repository.findByLocationNear(maine, 100000)
            println "Near results: ${nearResults.size()}"
        }
    }
}
// end::near-usage[]

// tag::within-usage[]
class WithinExample {

    static void main(String[] args) {
        def ctx = ApplicationContext.run()
        ctx.use { applicationContext ->
            def repository = applicationContext.getBean(IndexedBookRepository)
            def factory = new GeometryFactory()

            // Create a bounding box (polygon) around Maine
            // Coordinates must be in (longitude, latitude) order
            Geometry maineBox = factory.createPolygon([
                new Coordinate(-71.0, 43.0),  // Southwest corner
                new Coordinate(-67.0, 43.0),  // Southeast corner
                new Coordinate(-67.0, 47.0),  // Northeast corner
                new Coordinate(-71.0, 47.0),  // Northwest corner
                new Coordinate(-71.0, 43.0)   // Close the ring
            ] as Coordinate[])

            // Find books within the bounding box
            def withinResults = repository.findByLocationWithin(maineBox)
            println "Within results: ${withinResults.size()}"
        }
    }
}
// end::within-usage[]

// tag::intersects-usage[]
class IntersectsExample {

    static void main(String[] args) {
        def ctx = ApplicationContext.run()
        ctx.use { applicationContext ->
            def repository = applicationContext.getBean(IndexedBookRepository)
            def factory = new GeometryFactory()

            // Create a line that crosses Maine
            // Coordinates must be in (longitude, latitude) order
            Geometry line = factory.createLineString([
                new Coordinate(-70.0, 44.0),
                new Coordinate(-68.0, 46.0)
            ] as Coordinate[])

            // Find books whose location intersects the line
            def intersectsResults = repository.findByLocationIntersects(line)
            println "Intersects results: ${intersectsResults.size()}"
        }
    }
}
// end::intersects-usage[]
