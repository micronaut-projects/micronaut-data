package io.micronaut.data.model.geo

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.convert.GeometryWktConverter
import spock.lang.Specification

final class GeometryImmutabilitySpec extends Specification {

    void '#type snapshots component lists and exposes immutable components'() {
        given:
        def callerComponents = new ArrayList(components)
        def geometry = construct(callerComponents)
        def expected = construct(new ArrayList(components))
        def converter = new GeometryWktConverter()
        def wkt = converter.convertToPersistedValue(geometry, ConversionContext.DEFAULT)
        def hashCode = geometry.hashCode()
        def lookup = [(geometry): 'saved']

        when:
        callerComponents.clear()
        callerComponents.add(null)

        then:
        geometry == expected
        geometry.hashCode() == hashCode
        lookup[geometry] == 'saved'
        converter.convertToPersistedValue(geometry, ConversionContext.DEFAULT) == wkt

        when:
        readComponents(geometry).clear()

        then:
        thrown(UnsupportedOperationException)

        where:
        type                 | components                  | construct                             | readComponents
        'MultiPoint'         | [new Point(1d, 2d)]         | { new MultiPoint(it) }                | { it.points() }
        'LineString'         | ring().points()             | { new LineString(it) }                | { it.points() }
        'MultiLineString'    | [ring()]                    | { new MultiLineString(it) }           | { it.lineStrings() }
        'Polygon'            | [ring()]                    | { new Polygon(it) }                   | { it.lineStrings() }
        'MultiPolygon'       | [new Polygon([ring()])]     | { new MultiPolygon(it) }              | { it.polygons() }
        'GeometryCollection' | [new Point(1d, 2d), ring()] | { new GeometryCollection(it) }        | { it.geometries() }
    }

    void 'nested geometries retain valid rings after caller lists change'() {
        given:
        def points = new ArrayList(ring().points())
        def rings = new ArrayList([new LineString(points)])
        def polygons = new ArrayList([new Polygon(rings)])
        def geometries = new ArrayList([new MultiPolygon(polygons)])
        def collection = new GeometryCollection(geometries)
        def expected = new GeometryCollection([new MultiPolygon([new Polygon([ring()])])])

        when:
        points.removeLast()
        rings.clear()
        polygons.clear()
        geometries.clear()

        then:
        collection == expected
    }

    private static LineString ring() {
        new LineString([
                new Point(0d, 0d),
                new Point(4d, 0d),
                new Point(4d, 4d),
                new Point(0d, 0d)
        ])
    }
}
