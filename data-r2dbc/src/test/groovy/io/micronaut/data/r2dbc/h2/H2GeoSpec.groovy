package io.micronaut.data.r2dbc.h2

import groovy.transform.Memoized
import io.micronaut.data.model.geo.Geometry
import io.micronaut.data.model.geo.GeometryCollection
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.geo.Polygon
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktGeographyRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class H2GeoSpec extends AbstractGeoSpec implements H2TestPropertyProvider {

    private static final String H2_INIT = 'CREATE%20ALIAS%20IF%20NOT%20EXISTS%20H2GIS_SPATIAL%20FOR%20%22org.h2gis.functions.factory.H2GISFunctions.load%22%5C%3BCALL%20H2GIS_SPATIAL%28%29'
    private static final String H2_URL_PROPERTIES = 'LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=10;INIT=' + H2_INIT

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

    @Memoized
    @Override
    DeliveryDriverJsonRepository getDeliveryDriverJsonRepository() {
        return context.getBean(H2DeliveryDriverJsonRepository)
    }

    @Memoized
    @Override
    DeliveryDriverWktRepository getDeliveryDriverWktRepository() {
        return context.getBean(H2DeliveryDriverWktRepository)
    }

    @Memoized
    @Override
    DeliveryDriverWktGeographyRepository getDeliveryDriverWktGeographyRepository() {
        return context.getBean(H2DeliveryDriverWktGeographyRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }

    @Override
    Map<String, String> getH2DataSourceProperties(String dataSourceName) {
        def prefix = 'r2dbc.datasources.' + dataSourceName
        return [
                (prefix + '.url')            : "r2dbc:h2:mem:///${dataSourceName};${H2_URL_PROPERTIES}",
                (prefix + '.schema-generate'): schemaGenerate().name(),
                (prefix + '.dialect')        : 'h2',
                (prefix + '.username')       : '',
                (prefix + '.password')       : '',
                (prefix + '.packages')       : packages(),
        ] as Map<String, String>
    }

    @Override
    protected boolean supportsGeographyDatabaseType() {
        return false
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
