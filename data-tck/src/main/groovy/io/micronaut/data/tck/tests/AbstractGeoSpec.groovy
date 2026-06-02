package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.geo.Geometry
import io.micronaut.data.model.geo.GeometryCollection
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.MultiLineString
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.MultiPolygon
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.geo.Polygon
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverJson
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverWkt
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverWktGeography
import io.micronaut.data.tck.jdbc.entities.geo.GeometryEntityJson
import io.micronaut.data.tck.jdbc.entities.geo.GeometryEntityWkt
import io.micronaut.data.tck.jdbc.entities.geo.HotelJson
import io.micronaut.data.tck.jdbc.entities.geo.HotelWkt
import io.micronaut.data.tck.jdbc.entities.geo.Location
import io.micronaut.data.tck.jdbc.entities.geo.School
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktGeographyRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assumptions.assumeTrue

abstract class AbstractGeoSpec extends Specification {

    abstract GeometryEntityJsonRepository getGeometryEntityJsonRepository()

    abstract GeometryEntityWktRepository getGeometryEntityWktRepository()

    abstract SchoolRepository getSchoolRepository()

    abstract HotelJsonRepository getHotelJsonRepository()

    abstract HotelWktRepository getHotelWktRepository()

    abstract DeliveryDriverJsonRepository getDeliveryDriverJsonRepository()

    abstract DeliveryDriverWktRepository getDeliveryDriverWktRepository()

    abstract DeliveryDriverWktGeographyRepository getDeliveryDriverWktGeographyRepository()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    void cleanup() {
        getGeometryEntityJsonRepository()?.deleteAll()
        getGeometryEntityWktRepository()?.deleteAll()
        getSchoolRepository()?.deleteAll()
        getHotelJsonRepository()?.deleteAll()
        getHotelWktRepository()?.deleteAll()
        getDeliveryDriverJsonRepository()?.deleteAll()
        getDeliveryDriverWktRepository()?.deleteAll()
    }

    void "test creating, reading and updating when json conversion used on embedded geometry type"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        Location location1 = new Location()
        location1.setPoint(new Point(2.0, 2.5))
        School school = new School()
        school.setName("school1")
        school.setLocation(location1)

        when:
        School savedSchool = getSchoolRepository().insert(school)

        then:
        savedSchool.id > 0

        when:
        Optional<School> foundSchool = getSchoolRepository().findById(savedSchool.id)

        then:
        foundSchool.isPresent()
        with (foundSchool.get()) {
            it.getName() == "school1"
            it.getLocation().getPoint().x() == 2.0d
            it.getLocation().getPoint().y() == 2.5d
        }

        when:
        Location location2 = new Location()
        location2.setPoint(new Point(3.0, 3.5))
        school.setLocation(location2)
        getSchoolRepository().update(school)
        foundSchool = getSchoolRepository().findById(savedSchool.id)

        then:
        foundSchool.isPresent()
        with (foundSchool.get()) {
            it.getName() == "school1"
            it.getLocation().getPoint().x() == 3.0d
            it.getLocation().getPoint().y() == 3.5d
        }
    }

    void "test creating, reading and updating when json conversion used on geometry type"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        GeometryEntityJson entity = new GeometryEntityJson()
        entity.setPoint(createPoint(1))
        entity.setMultiPoint(createMultiPoint(1))
        entity.setLineString(createLineString(1))
        entity.setMultiLineString(createMultiLineString(1))
        entity.setPolygon(createPolygon(1))
        entity.setMultiPolygon(createMultiPolygon(1))
        entity.setGeometryCollection(createGeometryCollection(3))

        when:
        GeometryEntityJson savedEntity = getGeometryEntityJsonRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeometryEntityJson> foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 1)
            assertMultiPoint(it.getMultiPoint(), 1)
            assertLineString(it.getLineString(), 1)
            assertMultiLineString(it.getMultiLineString(), 1)
            assertPolygon(it.getPolygon(), 1)
            assertMultiPolygon(it.getMultiPolygon(), 1)
            assertGeometryCollection(it.getGeometryCollection(), 3)
        }

        when:
        entity.setPoint(createPoint(2))
        entity.setMultiPoint(createMultiPoint(2))
        entity.setLineString(createLineString(2))
        entity.setMultiLineString(createMultiLineString(2))
        entity.setPolygon(createPolygon(2))
        entity.setMultiPolygon(createMultiPolygon(2))
        entity.setGeometryCollection(createGeometryCollection(4))
        getGeometryEntityJsonRepository().update(entity)
        foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertMultiLineString(it.getMultiLineString(), 2)
            assertPolygon(it.getPolygon(), 2)
            assertMultiPolygon(it.getMultiPolygon(), 2)
            assertGeometryCollection(it.getGeometryCollection(), 4)
        }
    }

    void "test delete when json conversion used on geometry type"() {
        assumeTrue(supportsGeometryJsonConversion())
        assumeTrue(supportsDeletingGeometryTypes())

        given:
        GeometryEntityJson entity = new GeometryEntityJson()
        entity.setPoint(createPoint(5))
        entity.setMultiPoint(createMultiPoint(5))
        entity.setLineString(createLineString(5))
        entity.setMultiLineString(createMultiLineString(5))
        entity.setPolygon(createPolygon(5))
        entity.setMultiPolygon(createMultiPolygon(5))
        entity.setGeometryCollection(createGeometryCollection(8))

        when:
        GeometryEntityJson savedEntity = getGeometryEntityJsonRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeometryEntityJson> foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 5)
            assertMultiPoint(it.getMultiPoint(), 5)
            assertLineString(it.getLineString(), 5)
            assertMultiLineString(it.getMultiLineString(), 5)
            assertPolygon(it.getPolygon(), 5)
            assertMultiPolygon(it.getMultiPolygon(), 5)
            assertGeometryCollection(it.getGeometryCollection(), 8)
        }

        when:
        entity.setMultiLineString(null)
        entity.setPolygon(null)
        entity.setMultiPolygon(null)
        entity.setGeometryCollection(null)
        getGeometryEntityJsonRepository().update(entity)
        foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 5)
            assertMultiPoint(it.getMultiPoint(), 5)
            assertLineString(it.getLineString(), 5)
            assertNull(it.getMultiLineString())
            assertNull(it.getPolygon())
            assertNull(it.getMultiPolygon())
            assertNull(it.getGeometryCollection())
        }
    }

    void "test crud when wkt conversion used on geometry type"() {
        given:
        GeometryEntityWkt entity = new GeometryEntityWkt()
        entity.setPoint(createPoint(1))
        entity.setMultiPoint(createMultiPoint(1))
        entity.setLineString(createLineString(1))
        entity.setMultiLineString(createMultiLineString(1))
        entity.setPolygon(createPolygon(1))
        entity.setMultiPolygon(createMultiPolygon(1))
        entity.setGeometryCollection(createGeometryCollection(3))

        when:
        GeometryEntityWkt savedEntity = getGeometryEntityWktRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeometryEntityWkt> foundEntity = getGeometryEntityWktRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 1)
            assertMultiPoint(it.getMultiPoint(), 1)
            assertLineString(it.getLineString(), 1)
            assertMultiLineString(it.getMultiLineString(), 1)
            assertPolygon(it.getPolygon(), 1)
            assertMultiPolygon(it.getMultiPolygon(), 1)
            assertGeometryCollection(it.getGeometryCollection(), 3)
        }

        when:
        entity.setPoint(createPoint(2))
        entity.setMultiPoint(createMultiPoint(2))
        entity.setLineString(createLineString(2))
        entity.setMultiLineString(createMultiLineString(2))
        entity.setPolygon(createPolygon(2))
        entity.setMultiPolygon(createMultiPolygon(2))
        entity.setGeometryCollection(createGeometryCollection(4))
        getGeometryEntityWktRepository().update(entity)
        foundEntity = getGeometryEntityWktRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertMultiLineString(it.getMultiLineString(), 2)
            assertPolygon(it.getPolygon(), 2)
            assertMultiPolygon(it.getMultiPolygon(), 2)
            assertGeometryCollection(it.getGeometryCollection(), 4)
        }

        when:
        entity.setMultiLineString(null)
        entity.setPolygon(null)
        entity.setMultiPolygon(null)
        entity.setGeometryCollection(null)
        getGeometryEntityWktRepository().update(entity)
        foundEntity = getGeometryEntityWktRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertNull(it.getMultiLineString())
            assertNull(it.getPolygon())
            assertNull(it.getMultiPolygon())
            assertNull(it.getGeometryCollection())
        }
    }

    void "test findByLocationGeoWithin when json conversion is used"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        HotelJson inside1 = new HotelJson("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelJson inside2 = new HotelJson("Sunset Resort", new Point(12.0, 12.0))
        HotelJson outside = new HotelJson("Mountain View Hotel", new Point(30.0, 30.0))

        Polygon city = new Polygon([
                new LineString([
                        new Point(9.0, 9.0),
                        new Point(9.0, 15.0),
                        new Point(15.0, 15.0),
                        new Point(15.0, 9.0),
                        new Point(9.0, 9.0)
                ])
        ])

        when:
        getHotelJsonRepository().saveAll(List.of(inside1, inside2, outside))
        List<HotelJson> result = getHotelJsonRepository().findByLocationGeoWithin(city)
        List<String> names = result.stream()
                .map(HotelJson::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationGeoIntersects when json conversion used"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        HotelJson onRoute1 = new HotelJson("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelJson onRoute2 = new HotelJson("Sunset Resort", new Point(12.0, 12.0))
        HotelJson outside = new HotelJson("Mountain View Hotel", new Point(30.0, 30.0))

        LineString busRoute = new LineString([
                new Point(9.0, 9.0),
                new Point(15.0, 15.0)
        ])

        when:
        getHotelJsonRepository().saveAll(List.of(onRoute1, onRoute2, outside))
        List<HotelJson> result = getHotelJsonRepository().findByLocationGeoIntersects(busRoute)
        List<String> names = result.stream()
                .map(HotelJson::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationGeoWithin when wkt conversion used"() {
        given:
        HotelWkt inside1 = new HotelWkt("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelWkt inside2 = new HotelWkt("Sunset Resort", new Point(12.0, 12.0))
        HotelWkt outside = new HotelWkt("Mountain View Hotel", new Point(30.0, 30.0))

        Polygon city = new Polygon([
                new LineString([
                        new Point(9.0, 9.0),
                        new Point(9.0, 15.0),
                        new Point(15.0, 15.0),
                        new Point(15.0, 9.0),
                        new Point(9.0, 9.0)
                ])
        ])

        when:
        getHotelWktRepository().saveAll(List.of(inside1, inside2, outside))
        List<HotelWkt> result = getHotelWktRepository().findByLocationGeoWithin(city)
        List<String> names = result.stream()
                .map(HotelWkt::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationGeoIntersects when wkt conversion used"() {
        given:
        HotelWkt onRoute1 = new HotelWkt("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelWkt onRoute2 = new HotelWkt("Sunset Resort", new Point(12.0, 12.0))
        HotelWkt outside = new HotelWkt("Mountain View Hotel", new Point(30.0, 30.0))

        LineString busRoute = new LineString([
                new Point(9.0, 9.0),
                new Point(15.0, 15.0)
        ])

        when:
        getHotelWktRepository().saveAll(List.of(onRoute1, onRoute2, outside))
        List<HotelWkt> result = getHotelWktRepository().findByLocationGeoIntersects(busRoute)
        List<String> names = result.stream()
                .map(HotelWkt::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationNear on geometry database type when projected crs is used and json conversion applied"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        HotelJson nearby1 = new HotelJson("Grand Plaza Hotel", new Point(11.0, 11.0))
        HotelJson nearby2 = new HotelJson("Sunset Resort", new Point(12.0, 10.0))
        HotelJson farAway = new HotelJson("Mountain View Hotel", new Point(30.0, 30.0))

        Point center = new Point(10.0, 10.0)

        when:
        getHotelJsonRepository().saveAll(List.of(nearby1, nearby2, farAway))
        List<HotelJson> result = getHotelJsonRepository().findByLocationNear(center, 3d)
        List<String> names = result.stream()
                .map(HotelJson::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationNear on geometry database type when projected crs is used and wkt conversion applied"() {
        given:
        HotelWkt nearby1 = new HotelWkt("Grand Plaza Hotel", new Point(11.0, 11.0))
        HotelWkt nearby2 = new HotelWkt("Sunset Resort", new Point(12.0, 10.0))
        HotelWkt farAway = new HotelWkt("Mountain View Hotel", new Point(30.0, 30.0))

        Point center = new Point(10.0, 10.0)

        when:
        getHotelWktRepository().saveAll(List.of(nearby1, nearby2, farAway))
        List<HotelWkt> result = getHotelWktRepository().findByLocationNear(center, 3d)
        List<String> names = result.stream()
                .map(HotelWkt::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationNear on geometry database type when geographic crs is used and json conversion applied"() {
        assumeTrue(supportsGeometryTypeWithGeographicCrs())
        assumeTrue(supportsGeometryJsonConversion())

        given:
        DeliveryDriverJson nearby = new DeliveryDriverJson("Nearby Driver", DeliveryDriverJson.Status.AVAILABLE, new Point(-73.9757d, 40.7554d))
        DeliveryDriverJson closest = new DeliveryDriverJson("Closest Driver", DeliveryDriverJson.Status.AVAILABLE, new Point(-73.9827d, 40.7504d))
        DeliveryDriverJson busy = new DeliveryDriverJson("Busy Driver", DeliveryDriverJson.Status.BUSY, new Point(-73.9850d, 40.7488d))
        DeliveryDriverJson far = new DeliveryDriverJson("Far Driver", DeliveryDriverJson.Status.AVAILABLE, new Point(-73.9000d, 40.8000d))

        Point orderLocation = new Point(-73.9857, 40.7484)

        when:
        getDeliveryDriverJsonRepository().saveAll(List.of(nearby, closest, busy, far))
        List<DeliveryDriverJson> candidates = getDeliveryDriverJsonRepository().findByStatusAndLocationNear(
                DeliveryDriverJson.Status.AVAILABLE,
                orderLocation,
                5_000d
        )
        List<String> names = candidates.collect { it.name() }

        then:
        names.size() == 2
        names.contains("Nearby Driver")
        names.contains("Closest Driver")
    }

    void "test findByLocationNear on geometry database type when geographic crs is used and wkt conversion applied"() {
        assumeTrue(supportsGeometryTypeWithGeographicCrs())

        given:
        DeliveryDriverWkt nearby = new DeliveryDriverWkt("Nearby Driver", DeliveryDriverWkt.Status.AVAILABLE, new Point(-73.9757d, 40.7554d))
        DeliveryDriverWkt closest = new DeliveryDriverWkt("Closest Driver", DeliveryDriverWkt.Status.AVAILABLE, new Point(-73.9827d, 40.7504d))
        DeliveryDriverWkt busy = new DeliveryDriverWkt("Busy Driver", DeliveryDriverWkt.Status.BUSY, new Point(-73.9850d, 40.7488d))
        DeliveryDriverWkt far = new DeliveryDriverWkt("Far Driver", DeliveryDriverWkt.Status.AVAILABLE, new Point(-73.9000d, 40.8000d))

        Point orderLocation = new Point(-73.9857, 40.7484)

        when:
        getDeliveryDriverWktRepository().saveAll(List.of(nearby, closest, busy, far))
        List<DeliveryDriverWkt> candidates = getDeliveryDriverWktRepository().findByStatusAndLocationNear(
                DeliveryDriverWkt.Status.AVAILABLE,
                orderLocation,
                5_000d
        )
        List<String> names = candidates.collect { it.name() }

        then:
        names.size() == 2
        names.contains("Nearby Driver")
        names.contains("Closest Driver")
    }

    void "test findByLocationNear on geography database type when geographic crs is used and wkt conversion applied"() {
        assumeTrue(supportsGeographyDatabaseType())

        given:
        DeliveryDriverWktGeography nearby = new DeliveryDriverWktGeography("Nearby Driver", DeliveryDriverWktGeography.Status.AVAILABLE, new Point(-73.9757d, 40.7554d))
        DeliveryDriverWktGeography closest = new DeliveryDriverWktGeography("Closest Driver", DeliveryDriverWktGeography.Status.AVAILABLE, new Point(-73.9827d, 40.7504d))
        DeliveryDriverWktGeography busy = new DeliveryDriverWktGeography("Busy Driver", DeliveryDriverWktGeography.Status.BUSY, new Point(-73.9850d, 40.7488d))
        DeliveryDriverWktGeography far = new DeliveryDriverWktGeography("Far Driver", DeliveryDriverWktGeography.Status.AVAILABLE, new Point(-73.9000d, 40.8000d))

        Point orderLocation = new Point(-73.9857, 40.7484)

        when:
        getDeliveryDriverWktGeographyRepository().saveAll(List.of(nearby, closest, busy, far))
        List<DeliveryDriverWktGeography> candidates = getDeliveryDriverWktGeographyRepository().findByStatusAndLocationNear(
                DeliveryDriverWktGeography.Status.AVAILABLE,
                orderLocation,
                5_000d
        )
        List<String> names = candidates.collect { it.name() }

        then:
        names.size() == 2
        names.contains("Nearby Driver")
        names.contains("Closest Driver")
    }

    protected boolean supportsGeometryJsonConversion() {
        return true
    }

    protected boolean supportsDeletingGeometryTypes() {
        return true
    }

    protected boolean supportsGeometryTypeWithGeographicCrs() {
        return true
    }

    protected boolean supportsGeographyDatabaseType() {
        return true
    }

    Point createPoint(double x) {
        return new Point(x, x + 0.5)
    }

    void assertPoint(Point point, double x) {
        assert point != null
        assert point.x() == x
        assert point.y() == x + 0.5
    }

    MultiPoint createMultiPoint(int n) {
        return new MultiPoint([createPoint(n), createPoint(n + 1)])
    }

    void assertMultiPoint(MultiPoint multiPoint, int n) {
        assert multiPoint != null
        def points = multiPoint.points()
        assertPoint(points.get(0), n)
        assertPoint(points.get(1), n + 1)
    }

    LineString createLineString(int n) {
        return new LineString([createPoint(n), createPoint(n + 1)])
    }

    void assertLineString(LineString lineString, int n) {
        assert lineString != null
        def points = lineString.points()
        assertPoint(points.get(0), n)
        assertPoint(points.get(1), n + 1)
    }

    MultiLineString createMultiLineString(int n) {
        return new MultiLineString([createLineString(n + 10), createLineString(n + 20)])
    }

    void assertMultiLineString(MultiLineString multiLineString, int n) {
        assert multiLineString != null
        def lineStrings = multiLineString.lineStrings()
        assertLineString(lineStrings.get(0), n + 10)
        assertLineString(lineStrings.get(1), n + 20)
    }

    Polygon createPolygon(int n) {
        return new Polygon([
                new LineString([
                        new Point(n + 0.0, n + 0.0),
                        new Point(n + 4.0, n + 0.0),
                        new Point(n + 4.0, n + 3.0),
                        new Point(n + 0.0, n + 3.0),
                        new Point(n + 0.0, n + 0.0)
                ]),
                new LineString([
                        new Point(n + 0.5, n + 0.5),
                        new Point(n + 2.5, n + 0.5),
                        new Point(n + 2.5, n + 2.5),
                        new Point(n + 0.5, n + 0.5)
                ])
        ])
    }

    void assertPolygon(Polygon polygon, int n) {
        def lineStrings = polygon.lineStrings()
        assert lineStrings.size() == 2

        def points1 = lineStrings.get(0).points()
        assert points1*.x() == [n + 0.0d, n + 4.0d, n + 4.0d, n + 0.0d, n + 0.0d]
        assert points1*.y() == [n + 0.0d, n + 0.0d, n + 3.0d, n + 3.0d, n + 0.0d]

        def points2 = lineStrings.get(1).points()
        assert points2*.x() == [n + 0.5d, n + 2.5d, n + 2.5d, n + 0.5d]
        assert points2*.y() == [n + 0.5d, n + 0.5d, n + 2.5d, n + 0.5d]
    }

    MultiPolygon createMultiPolygon(int n) {
        return new MultiPolygon([createPolygon(n + 10), createPolygon(n + 20)])
    }

    void assertMultiPolygon(MultiPolygon multiPolygon, int n) {
        def polygons = multiPolygon.polygons()
        assertPolygon(polygons.get(0), n + 10)
        assertPolygon(polygons.get(1), n + 20)
    }

    protected GeometryCollection createGeometryCollection(int n) {
        return new GeometryCollection([
                createPoint(n),
                createMultiPoint(n),
                createLineString(n),
                createMultiLineString(n),
                createPolygon(n),
                createMultiPolygon(n)
        ] as List<Geometry>)
    }

    protected void assertGeometryCollection(GeometryCollection geometryCollection, int n) {
        def geometries = geometryCollection.geometries()
        assertPoint((Point) geometries.get(0), n)
        assertMultiPoint((MultiPoint) geometries.get(1), n)
        assertLineString((LineString) geometries.get(2), n)
        assertMultiLineString((MultiLineString) geometries.get(3), n)
        assertPolygon((Polygon) geometries.get(4), n)
        assertMultiPolygon((MultiPolygon) geometries.get(5), n)
    }
}
