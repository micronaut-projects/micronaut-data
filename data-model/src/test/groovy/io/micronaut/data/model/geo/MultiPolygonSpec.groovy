package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class MultiPolygonSpec extends Specification {

    void 'constructor accepts non-empty polygons'() {
        expect:
        new MultiPolygon([polygon(), anotherPolygon()]).polygons().size() == 2
    }

    @Unroll
    void 'constructor rejects invalid polygons #polygons'() {
        when:
        new MultiPolygon(polygons)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'MultiPolygon requires at least one Polygon'

        where:
        polygons << [null, []]
    }

    void 'asCoords returns polygon coordinates'() {
        expect:
        new MultiPolygon([polygon(), anotherPolygon()]).asCoords() == [
            [
                [[0d, 0d], [4d, 0d], [4d, 4d], [0d, 0d]],
                [[1d, 1d], [2d, 1d], [2d, 2d], [1d, 1d]]
            ],
            [
                [[10d, 10d], [14d, 10d], [14d, 14d], [10d, 10d]]
            ]
        ]
    }

    void 'fromCoords creates multi polygon'() {
        expect:
        MultiPolygon.fromCoords([
            [
                [[0d, 0d], [4d, 0d], [4d, 4d], [0d, 0d]],
                [[1d, 1d], [2d, 1d], [2d, 2d], [1d, 1d]]
            ],
            [
                [[10d, 10d], [14d, 10d], [14d, 14d], [10d, 10d]]
            ]
        ]) == new MultiPolygon([polygon(), anotherPolygon()])
    }

    @Unroll
    void 'fromCoords rejects empty coordinates #coords'() {
        when:
        MultiPolygon.fromCoords(coords)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates cannot be empty'

        where:
        coords << [null, []]
    }

    void 'fromCoords propagates polygon validation'() {
        when:
        MultiPolygon.fromCoords([[[[0d, 0d], [4d, 0d], [4d, 4d]]]])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Ring at index 0 must have at least 4 points (got 3)'
    }

    void 'fromCoords rejects null polygon coordinates'() {
        when:
        MultiPolygon.fromCoords([null])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates cannot contain null values'
    }

    private static Polygon polygon() {
        new Polygon([
            new LineString([
                new Point(0d, 0d),
                new Point(4d, 0d),
                new Point(4d, 4d),
                new Point(0d, 0d)
            ]),
            new LineString([
                new Point(1d, 1d),
                new Point(2d, 1d),
                new Point(2d, 2d),
                new Point(1d, 1d)
            ])
        ])
    }

    private static Polygon anotherPolygon() {
        new Polygon([
            new LineString([
                new Point(10d, 10d),
                new Point(14d, 10d),
                new Point(14d, 14d),
                new Point(10d, 10d)
            ])
        ])
    }
}
