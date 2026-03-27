package io.micronaut.data.jdbc.sqlserver

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityJsonRepository
import io.micronaut.data.tck.repositories.GeoEntityWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

import static org.junit.jupiter.api.Assertions.assertNull

class SqlServerGeoSpec extends AbstractGeoSpec implements MSSQLTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityJsonRepository getGeoEntityJsonRepository() {
        return context.getBean(MSGeoEntityJsonRepository)
    }

    @Memoized
    @Override
    GeoEntityWktRepository getGeoEntityWktRepository() {
        return context.getBean(MSGeoEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(MSSchoolRepository)
    }

    @Memoized
    MSGeoEntityWktGeomRepository getGeoEntityWktGeomRepository() {
        return context.getBean(MSGeoEntityWktGeomRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo", "io.micronaut.data.jdbc.sqlserver")
    }

    @Override
    protected boolean supportsGeometryJsonConversion() {
        // SqlServer doesn't have built-in functions for conversion
        // between json and internal geometry/geography data types
        return false
    }

    void "test crud of all geometry types when wkt conversion used together with sqlserver geometry type"() {
        given:
        GeoEntityWktGeom entity = new GeoEntityWktGeom()
        entity.setPoint(createPoint(1))
        entity.setMultiPoint(createMultiPoint(1))
        entity.setLineString(createLineString(1))
        entity.setMultiLineString(createMultiLineString(1))
        entity.setPolygon(createPolygon(1))
        entity.setMultiPolygon(createMultiPolygon(1))
        entity.setGeometryCollection(createGeometryCollection(3))

        when:
        GeoEntityWktGeom savedEntity = getGeoEntityWktGeomRepository().save(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeoEntityWktGeom> foundEntity = getGeoEntityWktGeomRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 1)
            assertMultiPoint(it.getMultiPoint(), 1)
            assertLineString(it.getLineString(), 1)
            assertMultiLineString(it.getMultiLineString(), 1)
            assertPolygon(it.getPolygon(), 1)
            assertMultiPolygon(it.getMultiPolygon(), 1)
            assertGeometryCollection(it.getGeometryCollection(), 3)
        }

        when:
        entity.setPoint(createPoint(2))
        entity.setMultiPoint(createMultiPoint(2))
        entity.setLineString(createLineString(2))
        entity.setMultiLineString(createMultiLineString(2))
        entity.setPolygon(createPolygon(2))
        entity.setMultiPolygon(createMultiPolygon(2))
        entity.setGeometryCollection(createGeometryCollection(4))
        getGeoEntityWktGeomRepository().update(entity)
        foundEntity = getGeoEntityWktGeomRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertMultiLineString(it.getMultiLineString(), 2)
            assertPolygon(it.getPolygon(), 2)
            assertMultiPolygon(it.getMultiPolygon(), 2)
            assertGeometryCollection(it.getGeometryCollection(), 4)
        }

        when:
        entity.setMultiLineString(null)
        entity.setPolygon(null)
        entity.setMultiPolygon(null)
        entity.setGeometryCollection(null)
        getGeoEntityWktGeomRepository().update(entity)
        foundEntity = getGeoEntityWktGeomRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertNull(it.getMultiLineString())
            assertNull(it.getPolygon())
            assertNull(it.getMultiPolygon())
            assertNull(it.getGeometryCollection())
        }
    }
}
