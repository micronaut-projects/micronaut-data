package io.micronaut.data.jdbc.h2

import groovy.transform.Memoized
import io.micronaut.data.model.geo.Geometry
import io.micronaut.data.model.geo.GeometryCollection
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.geo.Polygon
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class H2GeoSpec extends AbstractGeoSpec implements H2TestPropertyProvider {

    private static final String H2_URL_PROPERTIES = 'LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;INIT=' +
            'CREATE ALIAS IF NOT EXISTS H2GIS_SPATIAL FOR "org.h2gis.functions.factory.H2GISFunctions.load"\\;CALL H2GIS_SPATIAL()'

    @Memoized
    @Override
    GeometryEntityJsonRepository getGeometryEntityJsonRepository() {
        return context.getBean(H2GeometryEntityJsonRepository)
    }

    @Memoized
    @Override
    GeometryEntityWktRepository getGeometryEntityWktRepository() {
        return context.getBean(H2GeometryEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(H2SchoolRepository)
    }

    @Memoized
    @Override
    HotelJsonRepository getHotelJsonRepository() {
        return context.getBean(H2HotelJsonRepository)
    }

    @Memoized
    @Override
    HotelWktRepository getHotelWktRepository() {
        return context.getBean(H2HotelWktRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }

    @Override
    Map<String, String> getH2DataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        return [
                (prefix + '.url')            : "jdbc:h2:mem:${dataSourceName};${H2_URL_PROPERTIES}",
                (prefix + '.schema-generate'): schemaGenerate(),
                (prefix + '.dialect')        : 'h2',
                (prefix + '.username')       : '',
                (prefix + '.password')       : '',
                (prefix + '.packages')       : packages(),
                (prefix + '.driverClassName'): "org.h2.Driver"
        ] as Map<String, String>
    }

    @Override
    protected GeometryCollection createGeometryCollection(int n) {
        return new GeometryCollection([
                createPoint(n),
                createLineString(n),
                createPolygon(n)
        ] as List<Geometry>)
    }

    @Override
    protected void assertGeometryCollection(GeometryCollection geometryCollection, int n) {
        def geometries = geometryCollection.geometries()
        assertPoint((Point) geometries.get(0), n)
        assertLineString((LineString) geometries.get(1), n)
        assertPolygon((Polygon) geometries.get(2), n)
    }
}
