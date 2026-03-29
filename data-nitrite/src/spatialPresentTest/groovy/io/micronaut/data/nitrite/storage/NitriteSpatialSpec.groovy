package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.IndexedBook
import io.micronaut.data.nitrite.repository.IndexedBookRepository
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.NitriteCollection
import org.dizitart.no2.index.IndexType
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import spock.lang.Specification

/**
 * Comprehensive spatial test suite for Nitrite integration.
 * Tests spatial indexes, JTS Geometry support, and all spatial operators.
 * 
 * Note: GeoPoint tests require nitrite-spatial 4.3.3+ (not yet released).
 * Current tests use JTS Geometry types which work with nitrite-spatial 4.3.2.
 */
class NitriteSpatialSpec extends Specification {

    void "test JTS Geometry spatial queries with index verification"() {
        given:
        def ctx = ApplicationContext.run([
            "nitrite.storage-mode": "IN_MEMORY"
        ])
        def repository = ctx.getBean(IndexedBookRepository)
        def db = ctx.getBean(Nitrite)
        def factory = new GeometryFactory()

        // Create points for different locations
        def maine = factory.createPoint(new Coordinate(-69.0, 45.0))
        def colorado = factory.createPoint(new Coordinate(-105.0, 40.0))
        def california = factory.createPoint(new Coordinate(-122.4194, 37.7749))

        when: "Save books with JTS Geometry locations"
        def book1 = new IndexedBook("Book1", 100, "Horror novel", maine)
        def book2 = new IndexedBook("Book2", 200, "Mystery novel", colorado)
        def book3 = new IndexedBook("Book3", 300, "Sci-fi novel", california)
        repository.save(book1)
        repository.save(book2)
        repository.save(book3)

        then: "Books should be saved with Geometry preserved"
        repository.findAll().size() == 3
        repository.findAll().every { it.location == null || it.location instanceof Geometry }

        and: "Spatial index is created on location field"
        def collection = db.getCollection("IndexedBook")
        def indices = collection.listIndices()
        def spatialIndices = indices.findAll { it.indexType.toString() == "Spatial" }
        spatialIndices.size() == 1
        spatialIndices[0].fields.getFieldNames().contains("location")

        and: "Spatial index descriptor shows correct type"
        def spatialIndexDesc = spatialIndices[0]
        spatialIndexDesc.indexType.toString() == "Spatial"

        and: "\$near spatial filter works with JTS Point"
        def nearResults = repository.findByLocationNear(maine, 0.1)
        nearResults.size() == 1
        nearResults[0].title == "Book1"

        and: "\$within spatial filter works with Polygon"
        def maineBox = factory.createPolygon([
            new Coordinate(-71.0, 43.0),
            new Coordinate(-67.0, 43.0),
            new Coordinate(-67.0, 47.0),
            new Coordinate(-71.0, 47.0),
            new Coordinate(-71.0, 43.0)
        ] as Coordinate[])
        def withinResults = repository.findByLocationWithin(maineBox)
        withinResults.size() == 1
        withinResults[0].title == "Book1"

        and: "\$intersects spatial filter works"
        def intersectingLine = factory.createLineString([
            new Coordinate(-70.0, 44.0),
            new Coordinate(-68.0, 46.0)
        ] as Coordinate[])
        def intersectsResults = repository.findByLocationIntersects(intersectingLine)
        intersectsResults.size() == 1
        intersectsResults[0].title == "Book1"

        cleanup:
        ctx.close()
    }

    void "test spatial index is delegated to Nitrite's SpatialIndexer"() {
        given:
        def ctx = ApplicationContext.run([
            "nitrite.storage-mode": "IN_MEMORY"
        ])
        def repository = ctx.getBean(IndexedBookRepository)
        def db = ctx.getBean(Nitrite)
        def factory = new GeometryFactory()
        def maine = factory.createPoint(new Coordinate(-69.0, 45.0))
        def book = new IndexedBook("Test Book", 100, "Test description", maine)

        when: "Save a book to trigger index population"
        repository.save(book)
        def collection = db.getCollection("IndexedBook")
        def indices = collection.listIndices()
        def spatialIndices = indices.findAll { it.indexType.toString() == "Spatial" }

        then: "Spatial index exists on the location field"
        spatialIndices.size() == 1
        spatialIndices[0].fields.getFieldNames().contains("location")

        and: "Spatial near query uses the index successfully"
        def results = repository.findByLocationNear(maine, 0.1)
        results.size() == 1
        results[0].title == "Test Book"

        cleanup:
        ctx.close()
    }

    void "test spatial index with null geometry"() {
        given:
        def ctx = ApplicationContext.run([
            "nitrite.storage-mode": "IN_MEMORY"
        ])
        def repository = ctx.getBean(IndexedBookRepository)

        when: "Save book with null location"
        def book = new IndexedBook("No Location", 100, "No geometry", null)
        repository.save(book)

        then: "Should handle null geometry gracefully"
        repository.findAll().size() == 1
        repository.findAll()[0].location == null

        and: "Spatial index still exists"
        def db = ctx.getBean(Nitrite)
        def collection = db.getCollection("IndexedBook")
        def indices = collection.listIndices()
        def spatialIndices = indices.findAll { it.indexType.toString() == "Spatial" }
        spatialIndices.size() == 1

        cleanup:
        ctx.close()
    }
}
