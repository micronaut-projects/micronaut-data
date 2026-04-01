package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class MultiPointSpec extends Specification {

    void 'constructor accepts non-empty points'() {
        expect:
        new MultiPoint([point(), new Point(3d, 4d)]).points().size() == 2
    }

    @Unroll
    void 'constructor rejects invalid points #points'() {
        when:
        new MultiPoint(points)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'MultiPoint requires at least one Point'

        where:
        points << [null, []]
    }

    void 'asCoords returns point coordinates'() {
        expect:
        new MultiPoint([point(), new Point(3d, 4d)]).asCoords() == [[1d, 2.5d], [3d, 4d]]
    }

    void 'fromCoords creates multi point'() {
        expect:
        MultiPoint.fromCoords([[1d, 2.5d], [3d, 4d]]) == new MultiPoint([point(), new Point(3d, 4d)])
    }

    @Unroll
    void 'fromCoords rejects empty coordinates #coords'() {
        when:
        MultiPoint.fromCoords(coords)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates cannot be empty'

        where:
        coords << [null, []]
    }

    void 'fromCoords propagates point validation'() {
        when:
        MultiPoint.fromCoords([[1d]])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates must have 2 elements'
    }

    void 'fromCoords rejects null coordinate pair'() {
        when:
        MultiPoint.fromCoords([[1d, 2.5d], null])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates cannot contain null values'
    }

    private static Point point() {
        new Point(1d, 2.5d)
    }
}
