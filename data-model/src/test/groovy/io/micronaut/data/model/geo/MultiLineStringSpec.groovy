package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class MultiLineStringSpec extends Specification {

    void 'constructor accepts non-empty line strings'() {
        expect:
        new MultiLineString([lineString(), anotherLineString()]).lineStrings().size() == 2
    }

    @Unroll
    void 'constructor rejects invalid line strings #lineStrings'() {
        when:
        new MultiLineString(lineStrings)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'MultiLineString requires at least one LineString'

        where:
        lineStrings << [null, []]
    }


    void 'constructor rejects null line string element'() {
        when:
        new MultiLineString([lineString(), null])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'MultiLineString cannot contain null values'
    }

    void 'asCoords returns nested line coordinates'() {
        expect:
        new MultiLineString([lineString(), anotherLineString()]).asCoords() == [
            [[1d, 2.5d], [3d, 4d]],
            [[10d, 11d], [12.25d, 13.5d]]
        ]
    }

    void 'fromCoords creates multi line string'() {
        expect:
        MultiLineString.fromCoords([
            [[1d, 2.5d], [3d, 4d]],
            [[10d, 11d], [12.25d, 13.5d]]
        ]) == new MultiLineString([lineString(), anotherLineString()])
    }

    @Unroll
    void 'fromCoords rejects empty coordinates #coords'() {
        when:
        MultiLineString.fromCoords(coords)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates cannot be empty'

        where:
        coords << [null, []]
    }

    void 'fromCoords propagates line string validation'() {
        when:
        MultiLineString.fromCoords([[[1d, 2.5d]]])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'LineString requires at least two Points'
    }

    void 'fromCoords rejects null line coordinates'() {
        when:
        MultiLineString.fromCoords([[[1d, 2.5d], [3d, 4d]], null])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates cannot contain null values'
    }

    private static LineString lineString() {
        new LineString([new Point(1d, 2.5d), new Point(3d, 4d)])
    }

    private static LineString anotherLineString() {
        new LineString([new Point(10d, 11d), new Point(12.25d, 13.5d)])
    }
}
