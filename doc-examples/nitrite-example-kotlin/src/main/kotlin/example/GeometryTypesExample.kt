package example

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point

// tag::geometry-types[]
object GeometryTypesExample {

    @JvmStatic
    fun main(args: Array<String>) {
        val factory = GeometryFactory()

        // Create a Point - a single coordinate (x, y)
        // IMPORTANT: JTS uses (longitude, latitude), NOT (latitude, longitude)
        val nyc: Point = factory.createPoint(Coordinate(-74.0060, 40.7128))

        // Use the geometry in your entity
        // entity.setLocation(nyc)
    }
}
// end::geometry-types[]
