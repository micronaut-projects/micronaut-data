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
import io.micronaut.json.JsonMapper
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification
import spock.lang.Unroll

final class GeometryJsonConverterSpec extends Specification {

    private final JsonMapper jsonMapper = JsonMapper.createDefault()
    private final GeometryJsonConverter converter = new GeometryJsonConverter(jsonMapper, null)

    @Unroll
    void "convert #geometry.class.simpleName to GeoJSON and back"() {
        when:
        def persisted = converter.convertToPersistedValue(geometry, ConversionContext.DEFAULT)
        def restored = converter.convertToEntityValue(persisted, ConversionContext.DEFAULT)

        then:
        persisted == expectedJson
        restored == geometry

        where:
        geometry                       || expectedJson
        point()                        || '{"type":"Point","coordinates":[1.0,2.5]}'
        multiPoint()                   || '{"type":"MultiPoint","coordinates":[[1.0,2.5],[3.0,4.0]]}'
        lineString()                   || '{"type":"LineString","coordinates":[[1.0,2.5],[3.0,4.0],[5.75,6.0]]}'
        multiLineString()              || '{"type":"MultiLineString","coordinates":[[[1.0,2.5],[3.0,4.0],[5.75,6.0]],[[10.0,11.0],[12.25,13.5]]]}'
        polygon()                      || '{"type":"Polygon","coordinates":[[[0.0,0.0],[4.0,0.0],[4.0,4.0],[0.0,0.0]],[[1.0,1.0],[2.0,1.0],[2.0,2.0],[1.0,1.0]] ]}'.replace(' ]', ']')
        multiPolygon()                 || '{"type":"MultiPolygon","coordinates":[[[[0.0,0.0],[4.0,0.0],[4.0,4.0],[0.0,0.0]],[[1.0,1.0],[2.0,1.0],[2.0,2.0],[1.0,1.0]]],[[[10.0,10.0],[14.0,10.0],[14.0,14.0],[10.0,10.0]]]]}'
        geometryCollection()           || '{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[9.0,9.0]},{"type":"LineString","coordinates":[[1.0,2.5],[3.0,4.0],[5.75,6.0]]},{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[7.5,8.25]},{"type":"MultiPoint","coordinates":[[1.0,2.5],[3.0,4.0]]}]}]}'
    }

    void 'convert null geometry to persisted value returns null'() {
        expect:
        converter.convertToPersistedValue(null, ConversionContext.DEFAULT) == null
    }

    @Unroll
    void 'convert empty json #persistedValue to entity returns null'() {
        expect:
        converter.convertToEntityValue(persistedValue, ConversionContext.DEFAULT) == null

        where:
        persistedValue << [null, '']
    }

    void 'convert whitespace-only json throws serialization exception'() {
        when:
        converter.convertToEntityValue('   ', ConversionContext.DEFAULT)

        then:
        def ex = thrown(SerializationException)
        ex.message == 'Failed to deserialize json [   ]'
    }

    void 'convert invalid json throws serialization exception'() {
        when:
        converter.convertToEntityValue('{', ConversionContext.DEFAULT)

        then:
        def ex = thrown(SerializationException)
        ex.message == 'Failed to deserialize json [{]'
    }

    void 'convert invalid coordinates propagate validation errors'() {
        when:
        converter.convertToEntityValue('{"type":"Point","coordinates":[1.0]}', ConversionContext.DEFAULT)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Coordinates must have 2 elements'
    }

    void 'prefers default mapper when oracle mapper is null'() {
        given:
        def defaultMapper = Mock(JsonMapper)
        def geometry = point()
        def converter = new GeometryJsonConverter(defaultMapper, null)

        when:
        def result = converter.convertToPersistedValue(geometry, ConversionContext.DEFAULT)

        then:
        1 * defaultMapper.writeValueAsString(_) >> 'default-json'
        result == 'default-json'
    }

    void 'prefers oracle mapper when provided'() {
        given:
        def defaultMapper = Mock(JsonMapper)
        def oracleMapper = Mock(ObjectMapper)
        def geometry = point()
        def converter = new GeometryJsonConverter(defaultMapper, oracleMapper)

        when:
        def result = converter.convertToPersistedValue(geometry, ConversionContext.DEFAULT)

        then:
        0 * defaultMapper.writeValueAsString(_)
        1 * oracleMapper.writeValueAsString(_) >> 'oracle-json'
        result == 'oracle-json'
    }

    void 'wraps mapper serialization io exceptions'() {
        given:
        def mapper = Mock(JsonMapper)
        def converter = new GeometryJsonConverter(mapper, null)

        when:
        converter.convertToPersistedValue(point(), ConversionContext.DEFAULT)

        then:
        1 * mapper.writeValueAsString(_) >> { throw new IOException('boom') }
        def ex = thrown(SerializationException)
        ex.message.startsWith('Failed to serialize GeoJson entity [')
        ex.cause instanceof IOException
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
