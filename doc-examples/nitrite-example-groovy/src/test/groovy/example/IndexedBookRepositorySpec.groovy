package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class IndexedBookRepositorySpec extends Specification {

    @Inject IndexedBookRepository repository

    @Shared GeometryFactory factory = new GeometryFactory()

    def cleanup() {
        repository.deleteAll()
    }

    def "spatial index creation"() {
        given:
        IndexedBook book = new IndexedBook("NYC Guide", 100)
        book.location = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        book.description = "A guide to New York City attractions and landmarks"

        when:
        repository.save(book)

        then:
        book.id != null
        book.location != null
    }

    def "near query finds books within radius"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))
        def philadelphia = factory.createPoint(new Coordinate(-75.1652, 39.9526))

        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.location = nyc
        book1.description = "New York City guide"

        IndexedBook book2 = new IndexedBook("Boston Guide", 80)
        book2.location = boston
        book2.description = "Boston travel guide"

        IndexedBook book3 = new IndexedBook("Philadelphia Guide", 60)
        book3.location = philadelphia
        book3.description = "Philadelphia history"

        repository.saveAll([book1, book2, book3])

        when:
        def results = repository.findByLocationNear(nyc, 100000.0)

        then:
        !results.isEmpty()
        results.any { it.title == "NYC Guide" }
    }

    def "within query filters by bounding box"() {
        given:
        def nycBox = factory.createPolygon([
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 40.5),
            new Coordinate(-73.5, 41.0),
            new Coordinate(-74.5, 41.0),
            new Coordinate(-74.5, 40.5)
        ] as Coordinate[])

        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.location = factory.createPoint(new Coordinate(-74.0060, 40.7128))

        IndexedBook book2 = new IndexedBook("Boston Guide", 80)
        book2.location = factory.createPoint(new Coordinate(-71.0589, 42.3601))

        repository.saveAll([book1, book2])

        when:
        def results = repository.findByLocationWithin(nycBox)

        then:
        results.size() == 1
        results[0].title == "NYC Guide"
    }

    def "intersects query finds matching books"() {
        given:
        def nycCoordinate = new Coordinate(-74.0060, 40.7128)
        def line = factory.createLineString([
            new Coordinate(-75.0, 40.0),
            nycCoordinate
        ] as Coordinate[])

        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.location = factory.createPoint(nycCoordinate)

        IndexedBook book2 = new IndexedBook("Boston Guide", 80)
        book2.location = factory.createPoint(new Coordinate(-71.0589, 42.3601))

        repository.saveAll([book1, book2])

        when:
        def results = repository.findByLocationIntersects(line)

        then:
        !results.isEmpty()
        results.any { it.title == "NYC Guide" }
    }

    // tag::derived-spatial-usage[]
    def "derived spatial queries"() {
        given:
        def nyc = factory.createPoint(new Coordinate(-74.0060, 40.7128))
        def boston = factory.createPoint(new Coordinate(-71.0589, 42.3601))
        IndexedBook nycBook = new IndexedBook("NYC Guide", 100)
        nycBook.location = nyc
        IndexedBook bostonBook = new IndexedBook("Boston Guide", 80)
        bostonBook.location = boston
        repository.saveAll([nycBook, bostonBook])

        def nycBox = factory.createPolygon([
            new Coordinate(-74.5, 40.5),
            new Coordinate(-73.5, 40.5),
            new Coordinate(-73.5, 41.0),
            new Coordinate(-74.5, 41.0),
            new Coordinate(-74.5, 40.5)
        ] as Coordinate[])
        def crossingNyc = factory.createLineString([
            new Coordinate(-75.0, 40.0),
            nyc.coordinate
        ] as Coordinate[])

        expect:
        repository.findByLocationNear(nyc, 0.5).any { it.title == "NYC Guide" }
        repository.findByLocationGeoWithin(nycBox).size() == 1
        repository.findByLocationGeoIntersects(crossingNyc).any { it.title == "NYC Guide" }
    }
    // end::derived-spatial-usage[]

    def "full-text search by description"() {
        given:
        IndexedBook book1 = new IndexedBook("NYC Guide", 100)
        book1.description = "A comprehensive guide to New York City attractions and restaurants"

        IndexedBook book2 = new IndexedBook("Boston Travel", 80)
        book2.description = "Explore Boston's historic sites and museums"

        IndexedBook book3 = new IndexedBook("Food Guide", 60)
        book3.description = "Best restaurants in major American cities"

        repository.saveAll([book1, book2, book3])

        when:
        def results = repository.searchByDescription("New York")

        then:
        !results.isEmpty()
        results.any { it.title == "NYC Guide" }
    }

    def "compound index allows multiple books with same title"() {
        given:
        IndexedBook book1 = new IndexedBook("Guide", 100)
        book1.description = "Short guide"

        IndexedBook book2 = new IndexedBook("Guide", 500)
        book2.description = "Comprehensive guide"

        repository.saveAll([book1, book2])

        expect:
        repository.findAll().size() == 2
    }
}
