package example

import io.micronaut.context.ApplicationContext
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon

// tag::near-usage[]
class SpatialExample {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val ctx = ApplicationContext.run()
            ctx.use { applicationContext ->
                val repository = applicationContext.getBean(IndexedBookRepository::class.java)
                val factory = GeometryFactory()

                // Create a point for Maine, USA (longitude, latitude order!)
                // IMPORTANT: JTS uses (x, y) = (longitude, latitude), NOT (latitude, longitude)
                val maine = factory.createPoint(Coordinate(-69.0, 45.0))

                // Find books within 100km of Maine
                val nearResults = repository.findByLocationNear(maine, 100000.0)
                // end::near-usage[]
                println("Near results: ${nearResults.size}")
                // tag::near-usage[]
            }
        }
    }
}
// end::near-usage[]

// tag::within-usage[]
class WithinExample {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val ctx = ApplicationContext.run()
            ctx.use { applicationContext ->
                val repository = applicationContext.getBean(IndexedBookRepository::class.java)
                val factory = GeometryFactory()

                // Create a bounding box (polygon) around Maine
                // Coordinates must be in (longitude, latitude) order
                val maineBox = factory.createPolygon(arrayOf(
                    Coordinate(-71.0, 43.0),  // Southwest corner
                    Coordinate(-67.0, 43.0),  // Southeast corner
                    Coordinate(-67.0, 47.0),  // Northeast corner
                    Coordinate(-71.0, 47.0),  // Northwest corner
                    Coordinate(-71.0, 43.0)   // Close the ring
                ))

                // Find books within the bounding box
                val withinResults = repository.findByLocationWithin(maineBox)
                // end::within-usage[]
                println("Within results: ${withinResults.size}")
                // tag::within-usage[]
            }
        }
    }
}
// end::within-usage[]

// tag::intersects-usage[]
class IntersectsExample {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val ctx = ApplicationContext.run()
            ctx.use { applicationContext ->
                val repository = applicationContext.getBean(IndexedBookRepository::class.java)
                val factory = GeometryFactory()

                // Create a line that crosses Maine
                // Coordinates must be in (longitude, latitude) order
                val line: LineString = factory.createLineString(arrayOf(
                    Coordinate(-70.0, 44.0),
                    Coordinate(-68.0, 46.0)
                ))

                // Find books whose location intersects the line
                val intersectsResults = repository.findByLocationIntersects(line)
                // end::intersects-usage[]
                println("Intersects results: ${intersectsResults.size}")
                // tag::intersects-usage[]
            }
        }
    }
}
// end::intersects-usage[]
