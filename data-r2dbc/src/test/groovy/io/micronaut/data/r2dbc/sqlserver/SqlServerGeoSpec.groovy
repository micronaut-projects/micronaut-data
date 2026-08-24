package io.micronaut.data.r2dbc.sqlserver

import groovy.transform.Memoized
import io.micronaut.data.model.geo.Point
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

import static org.junit.jupiter.api.Assertions.assertNull

class SqlServerGeoSpec extends AbstractGeoSpec implements SqlServerTestPropertyProvider {

    @Memoized
    @Override
    GeometryEntityJsonRepository getGeometryEntityJsonRepository() {
        return context.getBean(MSGeometryEntityJsonRepository)
    }

    @Memoized
    @Override
    GeometryEntityWktRepository getGeometryEntityWktRepository() {
        return context.getBean(MSGeometryEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(MSSchoolRepository)
    }

    @Memoized
    @Override
    HotelJsonRepository getHotelJsonRepository() {
        return context.getBean(MSHotelJsonRepository)
    }

    @Memoized
    @Override
    HotelWktRepository getHotelWktRepository() {
        return context.getBean(MSHotelWktRepository)
    }

    @Memoized
    @Override
    DeliveryDriverJsonRepository getDeliveryDriverJsonRepository() {
        return context.getBean(MSDeliveryDriverJsonRepository)
    }

    @Memoized
    @Override
    DeliveryDriverWktRepository getDeliveryDriverWktRepository() {
        return context.getBean(MSDeliveryDriverWktRepository)
    }

    @Memoized
    MSDeliveryDriverWktGeographyRepository getDeliveryDriverWktGeographyRepository() {
        return context.getBean(MSDeliveryDriverWktGeographyRepository)
    }

    @Memoized
    MSGeographyEntityWktRepository getGeographyEntityWktRepository() {
        return context.getBean(MSGeographyEntityWktRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo", "io.micronaut.data.r2dbc.sqlserver")
    }

    @Override
    protected boolean supportsGeometryJsonConversion() {
        // SqlServer doesn't have built-in functions for conversion
        // between json and internal geometry/geography data types
        return false
    }

    void "test creates, reads, updates, and clears geography with WKT conversion"() {
        given:
        GeographyEntityWkt entity = new GeographyEntityWkt()
        entity.setPoint(createPoint(1))
        entity.setMultiPoint(createMultiPoint(1))
        entity.setLineString(createLineString(1))
        entity.setMultiLineString(createMultiLineString(1))
        entity.setPolygon(createPolygon(1))
        entity.setMultiPolygon(createMultiPolygon(1))
        entity.setGeometryCollection(createGeometryCollection(3))

        when:
        GeographyEntityWkt savedEntity = getGeographyEntityWktRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeographyEntityWkt> foundEntity = getGeographyEntityWktRepository().findById(savedEntity.id)

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
        getGeographyEntityWktRepository().update(entity)
        foundEntity = getGeographyEntityWktRepository().findById(savedEntity.id)

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
        getGeographyEntityWktRepository().update(entity)
        foundEntity = getGeographyEntityWktRepository().findById(savedEntity.id)

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

    void "test findByLocationNear with an explicit geography column and WKT conversion"() {
        given:
        DeliveryDriverWktGeography nearby = new DeliveryDriverWktGeography("Nearby Driver", DeliveryDriverWktGeography.Status.AVAILABLE, new Point(-73.9757d, 40.7554d))
        DeliveryDriverWktGeography closest = new DeliveryDriverWktGeography("Closest Driver", DeliveryDriverWktGeography.Status.AVAILABLE, new Point(-73.9827d, 40.7504d))
        DeliveryDriverWktGeography busy = new DeliveryDriverWktGeography("Busy Driver", DeliveryDriverWktGeography.Status.BUSY, new Point(-73.9850d, 40.7488d))
        DeliveryDriverWktGeography far = new DeliveryDriverWktGeography("Far Driver", DeliveryDriverWktGeography.Status.AVAILABLE, new Point(-73.9000d, 40.8000d))

        Point orderLocation = new Point(-73.9857, 40.7484)

        when:
        getDeliveryDriverWktGeographyRepository().saveAll(List.of(nearby, closest, busy, far))
        List<DeliveryDriverWktGeography> candidates = getDeliveryDriverWktGeographyRepository().findByStatusAndLocationNear(
                DeliveryDriverWktGeography.Status.AVAILABLE,
                orderLocation,
                5_000d
        )
        List<String> names = candidates.collect { it.name() }

        then:
        names.size() == 2
        names.contains("Nearby Driver")
        names.contains("Closest Driver")
    }
}
