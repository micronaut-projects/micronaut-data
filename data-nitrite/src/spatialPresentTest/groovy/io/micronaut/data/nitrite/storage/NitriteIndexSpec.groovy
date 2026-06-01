package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.IndexedBook
import io.micronaut.data.nitrite.repository.IndexedBookRepository
import org.dizitart.no2.Nitrite
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import spock.lang.Specification

class NitriteIndexSpec extends Specification {

    void "test automated index creation and advanced filters"() {
        given:
        def ctx = ApplicationContext.run([
            "nitrite.storage-mode": "IN_MEMORY"
        ])
        def repository = ctx.getBean(IndexedBookRepository)
        def db = ctx.getBean(Nitrite)
        def factory = new GeometryFactory()
        // Note: JTS Coordinate uses (x, y) = (longitude, latitude) order
        def maine = factory.createPoint(new Coordinate(-69.0, 45.0))  // Maine: lon=-69, lat=45
        def colorado = factory.createPoint(new Coordinate(-105.0, 40.0))  // Colorado: lon=-105, lat=40

        when: "We perform a repository operation"
        // First trigger index creation by calling findAll on empty collection
        repository.findAll()

        def book1 = new IndexedBook("The Stand", 1000, "A post-apocalyptic horror novel", maine)
        def book2 = new IndexedBook("The Shining", 600, "A classic ghost story", colorado)
        repository.save(book1)
        repository.save(book2)

        then: "Books should be saved"
        repository.findAll().size() == 2

        and: "Indexes should be present in the collection"
        def collection = db.getCollection("IndexedBook")
        def indices = collection.listIndices()
        indices.size() > 0

        and: "Full-text search works"
        // Give Nitrite time to build the index
        sleep(100)
        // Try native Nitrite full-text search first
        def nativeResults = collection.find(org.dizitart.no2.filters.FluentFilter.where("description").text("horror")).toList()
        nativeResults.size() == 1

        def results = repository.searchByDescription("horror")
        results.size() == 1
        results[0].title == "The Stand"

        and: "Spatial index is created"
        def spatialIndices = indices.findAll { it.indexType.toString() == "Spatial" }
        spatialIndices.size() == 1

        and: "\$near spatial filter works"
        def nearResults = repository.findByLocationNear(maine, 0.1) // Small radius in coordinate units
        nearResults.size() == 1
        nearResults[0].title == "The Stand"

        and: "\$within spatial filter works"
        // Create a bounding box around Maine (lon: -71 to -67, lat: 43 to 47)
        def maineBox = factory.createPolygon([
            new Coordinate(-71.0, 43.0),
            new Coordinate(-67.0, 43.0),
            new Coordinate(-67.0, 47.0),
            new Coordinate(-71.0, 47.0),
            new Coordinate(-71.0, 43.0)
        ] as Coordinate[])
        def withinResults = repository.findByLocationWithin(maineBox)
        withinResults.size() == 1
        withinResults[0].title == "The Stand"

        and: "\$intersects spatial filter works"
        // Create a line that intersects with Maine point
        def intersectingLine = factory.createLineString([
            new Coordinate(-70.0, 44.0),
            new Coordinate(-68.0, 46.0)
        ] as Coordinate[])
        def intersectsResults = repository.findByLocationIntersects(intersectingLine)
        intersectsResults.size() == 1
        intersectsResults[0].title == "The Stand"

        cleanup:
        ctx.close()
    }
}
