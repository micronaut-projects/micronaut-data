package io.micronaut.data.document.mongodb

import com.mongodb.client.model.geojson.LineString
import com.mongodb.client.model.geojson.MultiLineString
import com.mongodb.client.model.geojson.MultiPoint
import com.mongodb.client.model.geojson.Point
import com.mongodb.client.model.geojson.Position
import io.micronaut.data.document.mongodb.entities.GeoEntity
import io.micronaut.data.document.mongodb.repositories.MongoGeoEntityRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class MongoGeoSpec extends Specification {

    @Inject
    @Shared
    MongoGeoEntityRepository mongoGeoEntityRepository

    def cleanup() {
        mongoGeoEntityRepository.deleteAll()
    }

    void "test crud"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setPoint(createPoint(1))
        entity.setMultiPoint(createMultiPoint(1))
        entity.setLineString(createLineString(1))
        entity.setMultiLineString(createMultiLineString(1))

        when:
        GeoEntity savedEntity = mongoGeoEntityRepository.save(entity)

        then:
        savedEntity != null

        when:
        Optional<GeoEntity> foundEntity = mongoGeoEntityRepository.findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 1)
            assertMultiPoint(it.getMultiPoint(), 1)
            assertLineString(it.getLineString(), 1)
            assertMultiLineString(it.getMultiLineString(), 1)
        }

        when:
        entity.setPoint(createPoint(2))
        entity.setMultiPoint(createMultiPoint(2))
        entity.setLineString(createLineString(2))
        entity.setMultiLineString(createMultiLineString(2))
        mongoGeoEntityRepository.update(entity)
        foundEntity = mongoGeoEntityRepository.findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertMultiLineString(it.getMultiLineString(), 2)
        }

        when:
        entity.setMultiLineString(null)
        mongoGeoEntityRepository.update(entity)
        foundEntity = mongoGeoEntityRepository.findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assert it.getMultiLineString() == null
        }
    }

    Position createPosition(double x) {
        return new Position(x, x + 0.5)
    }

    void assertPosition(Position position, double x) {
        assert position != null
        List<Double> values = position.getValues()
        assert values.get(0) == x
        assert values.get(1) == x + 0.5
    }

    Point createPoint(double x) {
        return new Point(createPosition(x))
    }

    void assertPoint(Point point, double x) {
        assert point != null
        assertPosition(point.getPosition(), x)
    }

    MultiPoint createMultiPoint(int n) {
        return new MultiPoint([createPosition(n), createPosition(n + 1)])
    }

    void assertMultiPoint(MultiPoint multiPoint, int n) {
        assert multiPoint != null
        def positions = multiPoint.getCoordinates()
        assertPosition(positions.get(0), n)
        assertPosition(positions.get(1), n + 1)
    }

    LineString createLineString(int n) {
        return new LineString([createPosition(n), createPosition(n + 1)])
    }

    void assertLineString(LineString lineString, int n) {
        assert lineString != null
        def positions = lineString.getCoordinates()
        assertPosition(positions.get(0), n)
        assertPosition(positions.get(1), n + 1)
    }

    MultiLineString createMultiLineString(int n) {
        LineString lineString1 = createLineString(n + 10)
        LineString lineString2 = createLineString(n + 20)
        return new MultiLineString([lineString1.getCoordinates(), lineString2.getCoordinates()])
    }

    void assertMultiLineString(MultiLineString multiLineString, int n) {
        assert multiLineString != null
        def lineStrings = multiLineString.getCoordinates()


        assertLineString(new LineString(lineStrings.get(0)), n + 10)
        assertLineString(new LineString(lineStrings.get(1)), n + 20)
    }
}
