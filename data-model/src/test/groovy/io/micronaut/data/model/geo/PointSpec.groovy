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
        null            || 'Coordinates cannot be empty'
        []              || 'Coordinates cannot be empty'
        [1d]            || 'Coordinates must have 2 elements'
        [1d, 2d, 3d]    || 'Coordinates must have 2 elements'
        [1d, null]      || 'Coordinates cannot contain null values'
    }
}
