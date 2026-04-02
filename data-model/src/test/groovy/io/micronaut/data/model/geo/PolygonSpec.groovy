package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class PolygonSpec extends Specification {

    void 'constructor accepts valid closed rings'() {
        expect:
        new Polygon([outerRing(), innerRing()]).lineStrings().size() == 2
    }

    @Unroll
    void 'constructor rejects invalid line strings #lineStrings'() {
        when:
        new Polygon(lineStrings)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Polygon requires at least one ring (outer boundary)'

        where:
        lineStrings << [null, []]
    }

    void 'constructor rejects ring with fewer than four points'() {
        when:
        new Polygon([new LineString([
            new Point(0d, 0d),
            new Point(1d, 1d),
            new Point(0d, 0d)
        ])])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Ring at index 0 must have at least 4 points (got 3)'
    }

    void 'constructor rejects ring that is not closed'() {
        when:
        new Polygon([new LineString([
            new Point(0d, 0d),
            new Point(4d, 0d),
            new Point(4d, 4d),
            new Point(0d, 4d)
        ])])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Ring at index 0 is not closed: the first point is not equal to the last point'
    }


    void 'constructor rejects null ring element'() {
        when:
        new Polygon([outerRing(), null])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Polygon cannot contain null LineStrings'
    }

    void 'asCoords returns ring coordinates'() {
        expect:
        new Polygon([outerRing(), innerRing()]).asCoords() == [
            [[0d, 0d], [4d, 0d], [4d, 4d], [0d, 0d]],
            [[1d, 1d], [2d, 1d], [2d, 2d], [1d, 1d]]
        ]
    }

    void 'fromCoords creates polygon'() {
        expect:
        Polygon.fromCoords([
            [[0d, 0d], [4d, 0d], [4d, 4d], [0d, 0d]],
            [[1d, 1d], [2d, 1d], [2d, 2d], [1d, 1d]]
        ]) == new Polygon([outerRing(), innerRing()])
    }

    @Unroll
    void 'fromCoords rejects empty coordinates #coords'() {
        when:
        Polygon.fromCoords(coords)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'List of LineString coordinates cannot be null nor empty'

        where:
        coords << [null, []]
    }

    void 'fromCoords propagates line string validation'() {
        when:
        Polygon.fromCoords([[[0d, 0d], [4d, 0d], [4d, 4d]]])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Ring at index 0 must have at least 4 points (got 3)'

        when:
        Polygon.fromCoords([null])

        then:
        ex = thrown(IllegalArgumentException)
        ex.message == 'List of Point coordinates cannot be null nor empty'
    }

    private static LineString outerRing() {
        new LineString([
            new Point(0d, 0d),
            new Point(4d, 0d),
            new Point(4d, 4d),
            new Point(0d, 0d)
        ])
    }

    private static LineString innerRing() {
        new LineString([
            new Point(1d, 1d),
            new Point(2d, 1d),
            new Point(2d, 2d),
            new Point(1d, 1d)
        ])
    }
}
