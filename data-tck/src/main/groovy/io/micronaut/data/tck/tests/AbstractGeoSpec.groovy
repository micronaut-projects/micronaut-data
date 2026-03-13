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
import io.micronaut.data.tck.jdbc.entities.GeoEntity
import io.micronaut.data.tck.repositories.GeoEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractGeoSpec extends Specification {

    abstract GeoEntityRepository getGeoEntityRepository()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    void "test saving, reading and updating an entity with Point type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setPoint(new Point(2.0, 2.5))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        foundEntity.get().getPoint().x() == 2.0d
        foundEntity.get().getPoint().y() == 2.5d

        when:
        entity.setPoint(new Point(3.0, 3.5))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        foundEntity.get().getPoint().x() == 3.0d
        foundEntity.get().getPoint().y() == 3.5d
    }

    /*void "test saving, reading and updating an entity with MultiPoint type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setMultiPoint(new MultiPoint([
                new Point(1.1, 2.1),
                new Point(3.1, 4.1)
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiPoint()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 1.1d
            it.points().get(0).y() == 2.1d
            it.points().get(1).x() == 3.1d
            it.points().get(1).y() == 4.1d
        }

        when:
        entity.setMultiPoint(new MultiPoint([
                new Point(5.1, 6.1),
                new Point(7.1, 8.1)
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiPoint()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 5.1d
            it.points().get(0).y() == 6.1d
            it.points().get(1).x() == 7.1d
            it.points().get(1).y() == 8.1d
        }
    }

    void "test saving, reading and updating an entity with LineString type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setLineString(new LineString([
                new Point(1.1, 2.1),
                new Point(3.1, 4.1)
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getLineString()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 1.1d
            it.points().get(0).y() == 2.1d
            it.points().get(1).x() == 3.1d
            it.points().get(1).y() == 4.1d
        }

        when:
        entity.setLineString(new LineString([
                new Point(5.1, 6.1),
                new Point(7.1, 8.1)
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getLineString()) {
            it.points()
            it.points().size() == 2
            it.points().get(0).x() == 5.1d
            it.points().get(0).y() == 6.1d
            it.points().get(1).x() == 7.1d
            it.points().get(1).y() == 8.1d
        }
    }

    void "test saving, reading and updating an entity with MultiLineString type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setMultiLineString(new MultiLineString([
                new LineString([
                        new Point(1.1, 1.2),
                        new Point(1.3, 1.4)
                ]),
                new LineString([
                        new Point(2.1, 2.2),
                        new Point(2.3, 2.4)
                ])
        ]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiLineString()) {
            it.lineStrings()
            it.lineStrings().size() == 2
            it.lineStrings().get(0).points().size() == 2
            it.lineStrings().get(0).points().get(0).x() == 1.1d
            it.lineStrings().get(0).points().get(0).y() == 1.2d
            it.lineStrings().get(0).points().get(1).x() == 1.3d
            it.lineStrings().get(0).points().get(1).y() == 1.4d
            it.lineStrings().get(1).points().size() == 2
            it.lineStrings().get(1).points().get(0).x() == 2.1d
            it.lineStrings().get(1).points().get(0).y() == 2.2d
            it.lineStrings().get(1).points().get(1).x() == 2.3d
            it.lineStrings().get(1).points().get(1).y() == 2.4d
        }

        when:
        entity.setMultiLineString(new MultiLineString([
                new LineString([
                        new Point(3.1, 3.2),
                        new Point(3.3, 3.4)
                ]),
                new LineString([
                        new Point(4.1, 4.2),
                        new Point(4.3, 4.4)
                ])
        ]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiLineString()) {
            it.lineStrings()
            it.lineStrings().size() == 2
            it.lineStrings().get(0).points().size() == 2
            it.lineStrings().get(0).points().get(0).x() == 3.1d
            it.lineStrings().get(0).points().get(0).y() == 3.2d
            it.lineStrings().get(0).points().get(1).x() == 3.3d
            it.lineStrings().get(0).points().get(1).y() == 3.4d
            it.lineStrings().get(1).points().size() == 2
            it.lineStrings().get(1).points().get(0).x() == 4.1d
            it.lineStrings().get(1).points().get(0).y() == 4.2d
            it.lineStrings().get(1).points().get(1).x() == 4.3d
            it.lineStrings().get(1).points().get(1).y() == 4.4d
        }
    }

    void "test saving, reading and updating an entity with Polygon type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setPolygon(createPolygon1())

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        assertPolygon1(foundEntity.get().getPolygon())

        when:
        entity.setPolygon(createPolygon3())
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        assertPolygon3(foundEntity.get().getPolygon())
    }

    void "test saving, reading and updating an entity with MultiPolygon type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setMultiPolygon(new MultiPolygon([createPolygon1(), createPolygon2()]))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiPolygon().polygons()) {
            it.size() == 2
            assertPolygon1(it.get(0))
            assertPolygon2(it.get(1))
        }

        when:
        entity.setMultiPolygon(new MultiPolygon([createPolygon3(), createPolygon4()]))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get().getMultiPolygon().polygons()) {
            assert it.size() == 2
            assertPolygon3(it.get(0))
            assertPolygon4(it.get(1))
        }
    }

    void "test saving, reading and updating an entity with GeometryCollection type"() {
        given:
        GeoEntity entity = new GeoEntity()
        entity.setGeometryCollection(new GeometryCollection([
                new Point(2.0, 2.5),
                new MultiPoint([
                        new Point(1.1, 2.1),
                        new Point(3.1, 4.1)
                ]),
                new LineString([
                        new Point(5.1, 6.1),
                        new Point(7.1, 8.1)
                ]),
                new MultiLineString([
                        new LineString([
                                new Point(1.1, 1.2),
                                new Point(1.3, 1.4)
                        ]),
                        new LineString([
                                new Point(2.1, 2.2),
                                new Point(2.3, 2.4)
                        ])
                ]),
                createPolygon1(),
                new MultiPolygon([createPolygon1(), createPolygon2()])
        ] as List<Geometry>))

        when:
        GeoEntity savedEntity = getGeoEntityRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntity> foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            def point = it.getPoint()
            point.x() == 2.0d
            point.y() == 2.5d

            def points1 = it.getMultiPoint().points()
            points1*.x() == [1.1d, 3.1d]
            points1*.y() == [2.1d, 4.1d]

            def points2 = it.getLineString().points()
            points2*.x() == [5.1d, 7.1d]
            points2*.y() == [6.1d, 8.1d]

            def lineStrings = it.getMultiLineString().lineStrings()
            def points3 = lineStrings.get(0).points()
            points3*.x() == [1.1d, 1.2d]
            points3*.y() == [1.3d, 1.4d]
            def points4 = lineStrings.get(1).points()
            points4*.x() == [2.1d, 2.2d]
            points4*.y() == [2.3d, 2.4d]

            assertPolygon1(it.getPolygon())

            def polygons = it.getMultiPolygon().polygons()
            assertPolygon1(polygons.get(0))
            assertPolygon2(polygons.get(1))
        }

        when:
        entity.setGeometryCollection(new GeometryCollection([
                new Point(4.0, 4.5),
                new MultiPoint([
                        new Point(11.1, 21.1),
                        new Point(31.1, 41.1)
                ]),
                new LineString([
                        new Point(51.1, 61.1),
                        new Point(71.1, 81.1)
                ]),
                new MultiLineString([
                        new LineString([
                                new Point(11.1, 11.2),
                                new Point(11.3, 11.4)
                        ]),
                        new LineString([
                                new Point(21.1, 21.2),
                                new Point(21.3, 21.4)
                        ])
                ]),
                createPolygon3(),
                new MultiPolygon([createPolygon3(), createPolygon4()])
        ] as List<Geometry>))
        getGeoEntityRepository().update(entity)
        foundEntity = getGeoEntityRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            def point = it.getPoint()
            point.x() == 4.0d
            point.y() == 4.5d

            def points1 = it.getMultiPoint().points()
            points1*.x() == [11.1d, 31.1d]
            points1*.y() == [21.1d, 41.1d]

            def points2 = it.getLineString().points()
            points2*.x() == [51.1d, 71.1d]
            points2*.y() == [61.1d, 81.1d]

            def lineStrings = it.getMultiLineString().lineStrings()
            def points3 = lineStrings.get(0).points()
            points3*.x() == [11.1d, 11.2d]
            points3*.y() == [11.3d, 11.4d]
            def points4 = lineStrings.get(1).points()
            points4*.x() == [21.1d, 21.2d]
            points4*.y() == [21.3d, 21.4d]

            assertPolygon3(it.getPolygon())

            def polygons = it.getMultiPolygon().polygons()
            assertPolygon3(polygons.get(0))
            assertPolygon4(polygons.get(1))
        }
    }*/

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
}
