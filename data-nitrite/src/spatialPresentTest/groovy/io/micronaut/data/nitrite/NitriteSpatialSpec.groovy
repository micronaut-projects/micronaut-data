package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.IndexedBook
import io.micronaut.data.nitrite.repository.IndexedBookRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteSpatialSpec extends Specification {

    @Inject IndexedBookRepository repository

    @Shared GeometryFactory factory = new GeometryFactory()

    def cleanup() {
        repository.deleteAll()
    }

    def "CRUD with Point geometry"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        IndexedBook book = new IndexedBook("NYC Guide", 100, "A guide to New York City", nyc)

        when: "saved"
        repository.save(book)

        then:
        book.id != null
        book.location != null

        when: "retrieved"
        def found = repository.findById(book.id).orElse(null)

        then:
        found != null
        found.location != null
        found.location.coordinate.x == nyc.coordinate.x
        found.location.coordinate.y == nyc.coordinate.y

        when: "updated with a LineString"
        def line = factory.createLineString([new Coordinate(-74.5, 40.5), new Coordinate(-73.5, 41.0)] as Coordinate[])
        found.location = line
        repository.update(found)
        def updated = repository.findById(book.id).orElse(null)

        then:
        updated.location != null
        updated.location.geometryType == "LineString"
    }

    def "save and retrieve Polygon geometry"() {
        given:
        def polygon = factory.createPolygon([
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 40.5),
            new Coordinate(-73.5, 41.0),
            new Coordinate(-74.5, 41.0),
            new Coordinate(-74.5, 40.5)
        ] as Coordinate[])
        IndexedBook book = new IndexedBook("NYC Area", 50, "NYC bounding area", polygon)

        when:
        repository.save(book)
        def found = repository.findById(book.id).orElse(null)

        then:
        found != null
        found.location != null
        found.location.geometryType == "Polygon"
    }

    def "\$near derived query finds books within radius"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))
        def philadelphia = factory.createPoint(new Coordinate(-75.1652, 39.9526))

        repository.saveAll([
            new IndexedBook("NYC Guide", 100, "New York City guide", nyc),
            new IndexedBook("Boston Guide", 80, "Boston travel guide", boston),
            new IndexedBook("Philadelphia Guide", 60, "Philadelphia history", philadelphia)
        ])

        when:
        def results = repository.findByLocationNear(nyc, 0.5)

        then:
        !results.isEmpty()
        results.any { it.title == "NYC Guide" }
    }

    def "\$near @Query and derived query produce same results"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))

        repository.saveAll([
            new IndexedBook("NYC Guide", 100, "New York City guide", nyc),
            new IndexedBook("Boston Guide", 80, "Boston travel guide", boston)
        ])

        when:
        def fromQuery = repository.findByLocationNearQuery(nyc, 0.5)
        def fromDerived = repository.findByLocationNear(nyc, 0.5)

        then:
        fromQuery*.title as Set == fromDerived*.title as Set
    }

    def "\$geoWithin derived query filters by bounding box"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))

        repository.saveAll([
            new IndexedBook("NYC Guide", 100, "New York City guide", nyc),
            new IndexedBook("Boston Guide", 80, "Boston travel guide", boston)
        ])

        def nycBox = factory.createPolygon([
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 40.5),
            new Coordinate(-73.5, 41.0),
            new Coordinate(-74.5, 41.0),
            new Coordinate(-74.5, 40.5)
        ] as Coordinate[])

        when:
        def results = repository.findByLocationGeoWithin(nycBox)

        then:
        results.size() == 1
        results[0].title == "NYC Guide"
    }

    def "\$geoIntersects derived query finds books whose location intersects a line"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))

        repository.saveAll([
            new IndexedBook("NYC Guide", 100, "New York City guide", nyc),
            new IndexedBook("Boston Guide", 80, "Boston travel guide", boston)
        ])

        def line = factory.createLineString([
            new Coordinate(-75.0, 40.0),
            nyc.coordinate
        ] as Coordinate[])

        when:
        def results = repository.findByLocationGeoIntersects(line)

        then:
        !results.isEmpty()
        results.any { it.title == "NYC Guide" }
    }

    def "\$near filter with LineString geometry as center hits Geometry branch in SpatialFilterFactory"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))
        repository.saveAll([
            new IndexedBook("NYC Guide", 100, "New York City guide", nyc),
            new IndexedBook("Boston Guide", 80, "Boston travel guide", boston)
        ])
        // LineString is a Geometry but not a Point — exercises the Geometry→getCoordinate branch
        def queryLine = factory.createLineString([
            new Coordinate(-74.0060, 40.7128),
            new Coordinate(-74.0100, 40.7200)
        ] as Coordinate[])

        when:
        def results = repository.findByLocationNear(queryLine, 0.5)

        then:
        !results.isEmpty()
        results.any { it.title == "NYC Guide" }
    }

    def "\$within @Query and derived \$geoWithin return consistent results"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))
        repository.saveAll([
            new IndexedBook("NYC Guide", 100, "NYC", nyc),
            new IndexedBook("Boston Guide", 80, "Boston", boston)
        ])
        def nycBox = factory.createPolygon([
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 40.5),
            new Coordinate(-73.5, 41.0),
            new Coordinate(-74.5, 41.0),
            new Coordinate(-74.5, 40.5)
        ] as Coordinate[])

        when:
        def viaQuery   = repository.findByLocationWithin(nycBox)
        def viaDerived = repository.findByLocationGeoWithin(nycBox)

        then:
        viaQuery*.title as Set == viaDerived*.title as Set
        viaQuery.size() == 1
        viaQuery[0].title == "NYC Guide"
    }

    def "\$intersects @Query and derived \$geoIntersects return consistent results"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        repository.save(new IndexedBook("NYC Guide", 100, "NYC", nyc))
        def line = factory.createLineString([
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 41.0)
        ] as Coordinate[])

        when:
        def viaQuery   = repository.findByLocationIntersects(line)
        def viaDerived = repository.findByLocationGeoIntersects(line)

        then:
        viaQuery*.title as Set == viaDerived*.title as Set
    }

    def "full-text search by description"() {
        given:
        repository.saveAll([
            new IndexedBook("NYC Guide", 100, "A comprehensive guide to New York City attractions and restaurants", null),
            new IndexedBook("Boston Travel", 80, "Explore Boston's historic sites and museums", null),
            new IndexedBook("Food Guide", 60, "Best restaurants in major American cities", null)
        ])
        sleep(100) // allow Nitrite full-text index to build

        when:
        def results = repository.searchByDescription("New York")

        then:
        !results.isEmpty()
        results.any { it.title == "NYC Guide" }
    }
}
