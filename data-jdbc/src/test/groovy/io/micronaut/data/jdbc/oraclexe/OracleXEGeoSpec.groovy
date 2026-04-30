package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.model.geo.GeometryCollection
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.geo.Polygon
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class OracleXEGeoSpec extends AbstractGeoSpec implements OracleTestPropertyProvider {

    @Memoized
    @Override
    GeometryEntityJsonRepository getGeometryEntityJsonRepository() {
        return context.getBean(OracleXEGeometryEntityJsonRepository)
    }

    @Memoized
    @Override
    GeometryEntityWktRepository getGeometryEntityWktRepository() {
        return context.getBean(OracleXEGeometryEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(OracleXESchoolRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        return [
                (prefix + '.db-type')                        : dbType(),
                (prefix + '.schema-generate')                : schemaGenerate(),
                (prefix + '.dialect')                        : dialect(),
                (prefix + '.packages')                       : packages(),
                (prefix + '.enabled')                        : dataSourceEnabled(dataSourceName),
                "test-resources.containers.oracle.image-name": "gvenzl/oracle-free",
                "test-resources.containers.oracle.image-tag" : "latest"
        ] as Map<String, String>
    }

    @Override
    protected boolean supportsDeletingGeometryTypes() {
        // SDO_UTIL.FROM_GEOJSON fails when NULL is passed to it.
        // The issue has been reported and until it gets fixed, this method should return false.
        return false
    }

    @Override
    protected void assertGeometryCollection(GeometryCollection geometryCollection, int n) {
        def geometries = geometryCollection.geometries()
        assertPoint((Point) geometries.get(0), n)
        assertMultiPoint((MultiPoint) geometries.get(1), n)
        assertLineString((LineString) geometries.get(2), n)
        // oracle SDO_GEOMETRY type supports collections, but the internal model is commonly handled
        // as a single-level collection of primitive elements (points/lines/polygons) rather than
        // "a collection containing another multi-collection object", so during GeoJSON → SDO_GEOMETRY conversion
        // the GeometryCollection containing one MultiLineString with two LineString(s) is flatten into
        // the GeometryCollection containing two LineString(s)
        assertLineString((LineString) geometries.get(3), n + 10)
        assertLineString((LineString) geometries.get(4), n + 20)
        assertPolygon((Polygon) geometries.get(5), n)
        // the same as for MultiLineString, the MultiPolygon is flatten into two Polygon(s)
        assertPolygon((Polygon) geometries.get(6), n + 10)
        assertPolygon((Polygon) geometries.get(7), n + 20)
    }
}
