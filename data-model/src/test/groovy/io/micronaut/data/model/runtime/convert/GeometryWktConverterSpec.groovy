package io.micronaut.data.model.runtime.convert

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.serialize.exceptions.SerializationException
import io.micronaut.data.model.geo.Geometry
import io.micronaut.data.model.geo.GeometryCollection
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.MultiLineString
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.MultiPolygon
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.geo.Polygon
import spock.lang.Specification
import spock.lang.Unroll

final class GeometryWktConverterSpec extends Specification {

    private final GeometryWktConverter converter = new GeometryWktConverter()

    @Unroll
    void "convert #geometry.class.simpleName to WKT and back"() {
        when:
        def persisted = converter.convertToPersistedValue(geometry, ConversionContext.DEFAULT)
        def restored = converter.convertToEntityValue(persisted, ConversionContext.DEFAULT)

        then:
        persisted == expectedWkt
        restored == geometry

        where:
        geometry                       || expectedWkt
        point()                        || 'POINT (1 2.5)'
        multiPoint()                   || 'MULTIPOINT (1 2.5, 3 4)'
        lineString()                   || 'LINESTRING (1 2.5, 3 4, 5.75 6)'
        multiLineString()              || 'MULTILINESTRING ((1 2.5, 3 4, 5.75 6), (10 11, 12.25 13.5))'
        polygon()                      || 'POLYGON ((0 0, 4 0, 4 4, 0 0), (1 1, 2 1, 2 2, 1 1))'
        multiPolygon()                 || 'MULTIPOLYGON (((0 0, 4 0, 4 4, 0 0), (1 1, 2 1, 2 2, 1 1)), ((10 10, 14 10, 14 14, 10 10)))'
        geometryCollection()           || 'GEOMETRYCOLLECTION(POINT (9 9), LINESTRING (1 2.5, 3 4, 5.75 6), GEOMETRYCOLLECTION(POINT (7.5 8.25), MULTIPOINT (1 2.5, 3 4)))'
    }

    void 'convert null geometry to persisted value returns null'() {
        expect:
        converter.convertToPersistedValue(null, ConversionContext.DEFAULT) == null
    }

    @Unroll
    void 'convert empty WKT #persistedValue to entity returns null'() {
        expect:
        converter.convertToEntityValue(persistedValue, ConversionContext.DEFAULT) == null

        where:
        persistedValue << [null, '']
    }

    void 'convert whitespace-only WKT throws serialization exception'() {
        when:
        converter.convertToEntityValue('   ', ConversionContext.DEFAULT)

        then:
        def ex = thrown(SerializationException)
        ex.message == 'Failed to deserialize WKT [   ]'
        ex.cause instanceof IllegalArgumentException
    }

    void 'convert trimmed WKT to entity value'() {
        expect:
        converter.convertToEntityValue('  POINT (1 2.5)  ', ConversionContext.DEFAULT) == point()
    }

    @Unroll
    void 'invalid WKT #persistedValue throws serialization exception'() {
        when:
        converter.convertToEntityValue(persistedValue, ConversionContext.DEFAULT)

        then:
        def ex = thrown(SerializationException)
        ex.message == "Failed to deserialize WKT [${persistedValue}]"
        ex.cause instanceof IllegalArgumentException

        where:
        persistedValue << [
            'POINT 1 2',
            'CIRCLE (1 2)',
            'POINT (1 2))',
            'GEOMETRYCOLLECTION(POINT (1 2), LINESTRING (0 0, 1 1)))'
        ]
    }

    private static Point point() {
        new Point(1d, 2.5d)
    }

    private static MultiPoint multiPoint() {
        new MultiPoint([
            point(),
            new Point(3d, 4d)
        ])
    }

    private static LineString lineString() {
        new LineString([
            point(),
            new Point(3d, 4d),
            new Point(5.75d, 6d)
        ])
    }

    private static MultiLineString multiLineString() {
        new MultiLineString([
            lineString(),
            new LineString([
                new Point(10d, 11d),
                new Point(12.25d, 13.5d)
            ])
        ])
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

    private static MultiPolygon multiPolygon() {
        new MultiPolygon([
            polygon(),
            new Polygon([
                new LineString([
                    new Point(10d, 10d),
                    new Point(14d, 10d),
                    new Point(14d, 14d),
                    new Point(10d, 10d)
                ])
            ])
        ])
    }

    private static GeometryCollection geometryCollection() {
        new GeometryCollection([
            new Point(9d, 9d),
            lineString(),
            new GeometryCollection([
                new Point(7.5d, 8.25d),
                multiPoint()
            ])
        ] as List<Geometry>)
    }
}
