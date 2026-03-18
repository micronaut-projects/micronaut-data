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
import io.micronaut.data.tck.jdbc.entities.geo.GeoEntity
import io.micronaut.data.tck.jdbc.entities.geo.Location
import io.micronaut.data.tck.jdbc.entities.geo.School
import io.micronaut.data.tck.repositories.GeoEntityRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractGeoSpec extends Specification {

    abstract GeoEntityRepository getGeoEntityRepository()

    abstract SchoolRepository getSchoolRepository()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    /*void "test school"() {
        given:
        Location location1 = new Location()
        location1.setPoint(new Point(2.0, 2.5))
        School school = new School()
        school.setName("school1")
        school.setLocation(location1)

        when:
        School savedSchool = getSchoolRepository().save(school)

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
    }*/

    void "test saving, reading and updating a geo entity"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setPoint(createPoint1())
        entity.setMultiPoint(createMultiPoint1())
        entity.setLineString(createLineString1())
        entity.setMultiLineString(createMultiLineString1())
        entity.setPolygon(createPolygon1())
        entity.setMultiPolygon(createMultiPolygon1())
        entity.setGeometryCollection(createGeometryCollection1())

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint1(it.getPoint())
            assertMultiPoint1(it.getMultiPoint())
            assertLineString1(it.getLineString())
            assertMultiLineString1(it.getMultiLineString())
            assertPolygon1(it.getPolygon())
            assertMultiPolygon1(it.getMultiPolygon())
            assertGeometryCollection1(it.getGeometryCollection())
        }

        when:
        entity.setPoint(createPoint2())
        entity.setMultiPoint(createMultiPoint2())
        entity.setLineString(createLineString2())
        entity.setMultiLineString(createMultiLineString2())
        entity.setPolygon(createPolygon3())
        entity.setMultiPolygon(createMultiPolygon2())
        entity.setGeometryCollection(createGeometryCollection2())
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint2(it.getPoint())
            assertMultiPoint2(it.getMultiPoint())
            assertLineString2(it.getLineString())
            assertMultiLineString2(it.getMultiLineString())
            assertPolygon3(it.getPolygon())
            assertMultiPolygon2(it.getMultiPolygon())
            assertGeometryCollection2(it.getGeometryCollection())
        }
    }

    Point createPoint1() {
        return new Point(2.0, 2.5)
    }

    void assertPoint1(Point point) {
        assert point != null
        assert point.x() == 2.0d
        assert point.y() == 2.5d
    }

    Point createPoint2() {
        return new Point(3.0, 3.5)
    }

    void assertPoint2(Point point) {
        assert point != null
        assert point.x() == 3.0d
        assert point.y() == 3.5d
    }

    MultiPoint createMultiPoint1() {
        return new MultiPoint([
                new Point(1.1, 2.1),
                new Point(3.1, 4.1)
        ])
    }

    void assertMultiPoint1(MultiPoint multiPoint) {
        assert multiPoint != null
        def points = multiPoint.points()
        assert points != null
        assert points*.x() == [1.1d, 3.1d]
        assert points*.y() == [2.1d, 4.1d]
    }

    MultiPoint createMultiPoint2() {
        return new MultiPoint([
                new Point(5.1, 6.1),
                new Point(7.1, 8.1)
        ])
    }

    void assertMultiPoint2(MultiPoint multiPoint) {
        assert multiPoint != null
        def points = multiPoint.points()
        assert points != null
        assert points*.x() == [5.1d, 7.1d]
        assert points*.y() == [6.1d, 8.1d]
    }

    LineString createLineString1() {
        return new LineString([
                new Point(1.1, 2.1),
                new Point(3.1, 4.1)
        ])
    }

    void assertLineString1(LineString lineString) {
        assert lineString != null
        def points = lineString.points()
        assert points*.x() == [1.1d, 3.1d]
        assert points*.y() == [2.1d, 4.1d]
    }

    LineString createLineString2() {
        return new LineString([
                new Point(5.1, 6.1),
                new Point(7.1, 8.1)
        ])
    }

    void assertLineString2(LineString lineString) {
        assert lineString != null
        def points = lineString.points()
        assert points*.x() == [5.1d, 7.1d]
        assert points*.y() == [6.1d, 8.1d]
    }

    MultiLineString createMultiLineString1() {
        return new MultiLineString([
                new LineString([
                        new Point(1.1, 1.2),
                        new Point(1.3, 1.4)
                ]),
                new LineString([
                        new Point(2.1, 2.2),
                        new Point(2.3, 2.4)
                ])
        ])
    }

    void assertMultiLineString1(MultiLineString multiLineString) {
        assert multiLineString != null
        def lineStrings = multiLineString.lineStrings()
        assert lineStrings.size() == 2
        def points1 = lineStrings.get(0).points()
        assert points1*.x() == [1.1d, 1.3d]
        assert points1*.y() == [1.2d, 1.4d]
        def points2 = lineStrings.get(1).points()
        assert points2*.x() == [2.1d, 2.3d]
        assert points2*.y() == [2.2d, 2.4d]
    }

    MultiLineString createMultiLineString2() {
        return new MultiLineString([
                new LineString([
                        new Point(3.1, 3.2),
                        new Point(3.3, 3.4)
                ]),
                new LineString([
                        new Point(4.1, 4.2),
                        new Point(4.3, 4.4)
                ])
        ])
    }

    void assertMultiLineString2(MultiLineString multiLineString) {
        assert multiLineString != null
        def lineStrings = multiLineString.lineStrings()
        assert lineStrings.size() == 2
        def points1 = lineStrings.get(0).points()
        assert points1*.x() == [3.1d, 3.3d]
        assert points1*.y() == [3.2d, 3.4d]
        def points2 = lineStrings.get(1).points()
        assert points2*.x() == [4.1d, 4.3d]
        assert points2*.y() == [4.2d, 4.4d]
    }

    Polygon createPolygon1() {
        return new Polygon([
                new LineString([
                        new Point(1.0, 1.0),
                        new Point(5.0, 1.0),
                        new Point(5.0, 2.5),
                        new Point(1.0, 2.5),
                        new Point(1.0, 1.0)
                ]),
                new LineString([
                        new Point(1.5, 1.5),
                        new Point(2.5, 1.5),
                        new Point(2.5, 2.0),
                        new Point(1.5, 1.5)
                ])
        ])
    }

    void assertPolygon1(Polygon polygon) {
        def lineStrings = polygon.lineStrings()
        assert lineStrings.size() == 2

        def points1 = lineStrings.get(0).points()
        assert points1*.x() == [1.0d, 5.0d, 5.0d, 1.0d, 1.0d]
        assert points1*.y() == [1.0d, 1.0d, 2.5d, 2.5d, 1.0d]

        def points2 = lineStrings.get(1).points()
        assert points2*.x() == [1.5d, 2.5d, 2.5d, 1.5d]
        assert points2*.y() == [1.5d, 1.5d, 2.0d, 1.5d]
    }

    Polygon createPolygon2() {
        return new Polygon([
                new LineString([
                        new Point(11.0, 11.0),
                        new Point(15.0, 11.0),
                        new Point(15.0, 12.5),
                        new Point(11.0, 12.5),
                        new Point(11.0, 11.0)
                ]),
                new LineString([
                        new Point(11.5, 11.5),
                        new Point(12.5, 11.5),
                        new Point(12.5, 12.0),
                        new Point(11.5, 11.5)
                ])
        ])
    }

    void assertPolygon2(Polygon polygon) {
        def lineStrings = polygon.lineStrings()
        assert lineStrings.size() == 2

        def points1 = lineStrings.get(0).points()
        assert points1*.x() == [11.0d, 15.0d, 15.0d, 11.0d, 11.0d]
        assert points1*.y() == [11.0d, 11.0d, 12.5d, 12.5d, 11.0d]

        def points2 = lineStrings.get(1).points()
        assert points2*.x() == [11.5d, 12.5d, 12.5d, 11.5d]
        assert points2*.y() == [11.5d, 11.5d, 12.0d, 11.5d]
    }

    Polygon createPolygon3() {
        return new Polygon([
                new LineString([
                        new Point(1.0, 1.0),
                        new Point(5.0, 1.0),
                        new Point(5.0, 2.5),
                        new Point(1.0, 2.5),
                        new Point(1.0, 1.0)
                ])
        ])
    }

    void assertPolygon3(Polygon polygon) {
        def lineStrings = polygon.lineStrings()
        assert lineStrings.size() == 1

        def points = lineStrings.get(0).points()
        assert points*.x() == [1.0d, 5.0d, 5.0d, 1.0d, 1.0d]
        assert points*.y() == [1.0d, 1.0d, 2.5d, 2.5d, 1.0d]
    }

    Polygon createPolygon4() {
        return new Polygon([
                new LineString([
                        new Point(11.0, 11.0),
                        new Point(15.0, 11.0),
                        new Point(15.0, 12.5),
                        new Point(11.0, 12.5),
                        new Point(11.0, 11.0)
                ])
        ])
    }

    void assertPolygon4(Polygon polygon) {
        def lineStrings = polygon.lineStrings()
        assert lineStrings.size() == 1

        def points = lineStrings.get(0).points()
        assert points*.x() == [11.0d, 15.0d, 15.0d, 11.0d, 11.0d]
        assert points*.y() == [11.0d, 11.0d, 12.5d, 12.5d, 11.0d]
    }

    MultiPolygon createMultiPolygon1() {
        return new MultiPolygon([createPolygon1(), createPolygon2()])
    }

    void assertMultiPolygon1(MultiPolygon multiPolygon) {
        def polygons = multiPolygon.polygons()
        assertPolygon1(polygons.get(0))
        assertPolygon2(polygons.get(1))
    }

    MultiPolygon createMultiPolygon2() {
        return new MultiPolygon([createPolygon3(), createPolygon4()])
    }

    void assertMultiPolygon2(MultiPolygon multiPolygon) {
        def polygons = multiPolygon.polygons()
        assertPolygon3(polygons.get(0))
        assertPolygon4(polygons.get(1))
    }

    GeometryCollection createGeometryCollection1() {
        return new GeometryCollection([
                createPoint1(),
                createLineString1(),
                createPolygon1()
        ] as List<Geometry>)
    }

    void assertGeometryCollection1(GeometryCollection geometryCollection) {
        def geometries = geometryCollection.geometries()
        assertPoint1((Point) geometries.get(0))
        assertLineString1((LineString) geometries.get(1))
        assertPolygon1((Polygon) geometries.get(2))
    }

    GeometryCollection createGeometryCollection2() {
        return new GeometryCollection([
                createPoint2(),
                createLineString2(),
                createPolygon3()
        ] as List<Geometry>)
    }

    void assertGeometryCollection2(GeometryCollection geometryCollection) {
        def geometries = geometryCollection.geometries()
        assertPoint2((Point) geometries.get(0))
        assertLineString2((LineString) geometries.get(1))
        assertPolygon3((Polygon) geometries.get(2))
    }
}
