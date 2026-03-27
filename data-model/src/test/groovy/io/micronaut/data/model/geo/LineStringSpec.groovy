package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class LineStringSpec extends Specification {

    void 'constructor accepts at least two points'() {
        expect:
        new LineString([point(), new Point(3d, 4d)]).points().size() == 2
    }

    @Unroll
    void 'constructor rejects invalid points #points'() {
        when:
        new LineString(points)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'LineString requires at least two Points'

        where:
        points << [null, [], [point()]]
    }

    void 'asCoords returns point coordinates in order'() {
        expect:
        new LineString([point(), new Point(3d, 4d), new Point(5.75d, 6d)]).asCoords() == [[1d, 2.5d], [3d, 4d], [5.75d, 6d]]
    }

    void 'fromCoords creates line string'() {
        expect:
        LineString.fromCoords([[1d, 2.5d], [3d, 4d]]) == new LineString([point(), new Point(3d, 4d)])
    }

    @Unroll
    void 'fromCoords rejects empty coordinates #coords'() {
        when:
        LineString.fromCoords(coords)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates cannot be empty'

        where:
        coords << [null, []]
    }

    void 'fromCoords propagates constructor validation for one point'() {
        when:
        LineString.fromCoords([[1d, 2.5d]])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'LineString requires at least two Points'
    }

    void 'fromCoords propagates point validation'() {
        when:
        LineString.fromCoords([[1d, 2.5d], [3d]])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates must have 2 elements'
    }

    private static Point point() {
        new Point(1d, 2.5d)
    }
}
