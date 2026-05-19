package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class GeometrySpec extends Specification {

    void 'sealed interface permits expected implementations'() {
        expect:
        Geometry.permittedSubclasses*.simpleName as Set == [
            'Point',
            'MultiPoint',
            'LineString',
            'MultiLineString',
            'Polygon',
            'MultiPolygon',
            'GeometryCollection'
        ] as Set
    }

    @Unroll
    void '#geometry.class.simpleName implements Geometry'() {
        expect:
        geometry instanceof Geometry

        where:
        geometry << [
            new Point(1d, 2d),
            new MultiPoint([new Point(1d, 2d)]),
            new LineString([new Point(1d, 2d), new Point(3d, 4d)]),
            new MultiLineString([new LineString([new Point(1d, 2d), new Point(3d, 4d)])]),
            new Polygon([new LineString([
                new Point(0d, 0d),
                new Point(4d, 0d),
                new Point(4d, 4d),
                new Point(0d, 0d)
            ])]),
            new MultiPolygon([new Polygon([new LineString([
                new Point(0d, 0d),
                new Point(4d, 0d),
                new Point(4d, 4d),
                new Point(0d, 0d)
            ])])]),
            new GeometryCollection([new Point(1d, 2d)])
        ]
    }
}
