package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class PointSpec extends Specification {

    void 'asCoords returns coordinates in order'() {
        expect:
        new Point(1d, 2.5d).asCoords() == [1d, 2.5d]
    }

    void 'asCoords returns immutable list'() {
        given:
        def coords = new Point(1d, 2.5d).asCoords()

        when:
        coords << 3d

        then:
        thrown(UnsupportedOperationException)
    }

    void 'fromCoords creates point'() {
        expect:
        Point.fromCoords([1d, 2.5d]) == new Point(1d, 2.5d)
    }

    @Unroll
    void 'fromCoords rejects invalid coordinates #coords'() {
        when:
        Point.fromCoords(coords)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == message

        where:
        coords          || message
        null            || 'List of coordinates cannot be null nor empty'
        []              || 'List of coordinates cannot be null nor empty'
        [1d]            || 'List of coordinates must have two values'
        [1d, 2d, 3d]    || 'List of coordinates must have two values'
        [1d, null]      || 'List of coordinates cannot contain null values'
    }

    void 'rejects non-finite coordinates (#x, #y)'() {
        when:
        new Point(x, y)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Point coordinates must be finite'

        when:
        Point.fromCoords([x, y])

        then:
        thrown(IllegalArgumentException)

        where:
        x                        | y
        Double.NaN               | 1d
        Double.POSITIVE_INFINITY | 1d
        Double.NEGATIVE_INFINITY | 1d
        1d                       | Double.NaN
        1d                       | Double.POSITIVE_INFINITY
        1d                       | Double.NEGATIVE_INFINITY
    }

    void 'accepts finite coordinates including extreme values'() {
        expect:
        new Point(x, y).asCoords() == [x, y]
        Point.fromCoords([x, y]) == new Point(x, y)

        where:
        x                 | y
        0d                | -0d
        -122.4194d        | 37.7749d
        Double.MAX_VALUE  | -Double.MAX_VALUE
        Double.MIN_VALUE  | -Double.MIN_VALUE
    }
}
