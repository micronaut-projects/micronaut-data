package io.micronaut.data.model.geo

import spock.lang.Specification
import spock.lang.Unroll

final class GeometryCollectionSpec extends Specification {

    void 'constructor accepts heterogeneous geometries'() {
        given:
        def collection = new GeometryCollection([new Point(1d, 2d), lineString(), new MultiPoint([new Point(3d, 4d)])])

        expect:
        collection.geometries().size() == 3
        collection.geometries()[0] instanceof Point
        collection.geometries()[1] instanceof LineString
        collection.geometries()[2] instanceof MultiPoint
    }

    void 'constructor preserves nested collections'() {
        given:
        def nested = new GeometryCollection([new Point(1d, 2d)])

        expect:
        new GeometryCollection([nested, lineString()]).geometries() == [nested, lineString()]
    }

    @Unroll
    void 'constructor rejects invalid geometries #geometries'() {
        when:
        new GeometryCollection(geometries)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'GeometryCollection requires at least one Geometry'

        where:
        geometries << [null, []]
    }

    private static LineString lineString() {
        new LineString([new Point(1d, 2d), new Point(3d, 4d)])
    }
}
