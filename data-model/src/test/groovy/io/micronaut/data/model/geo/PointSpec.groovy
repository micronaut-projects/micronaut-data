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
}
