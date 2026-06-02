package io.micronaut.data.r2dbc.postgres

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktGeographyRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.test.support.TestPropertyProviderFactory

import java.time.Duration

import static org.junit.jupiter.api.Assertions.assertNull

@TestResourcesScope("r2dbc-postgres-geo")
class PostgresGeoSpec extends AbstractGeoSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    GeometryEntityJsonRepository getGeometryEntityJsonRepository() {
        return context.getBean(PostgresGeometryEntityJsonRepository)
    }

    @Memoized
    @Override
    GeometryEntityWktRepository getGeometryEntityWktRepository() {
        return context.getBean(PostgresGeometryEntityWktRepository)
    }

    @Memoized
    PostgresGeographyEntityJsonRepository getGeographyEntityJsonRepository() {
        return context.getBean(PostgresGeographyEntityJsonRepository)
    }

    @Memoized
    PostgresGeographyEntityWktRepository getGeographyEntityWktRepository() {
        return context.getBean(PostgresGeographyEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(PostgresSchoolRepository)
    }

    @Memoized
    @Override
    HotelJsonRepository getHotelJsonRepository() {
        return context.getBean(PostgresHotelJsonRepository)
    }

    @Memoized
    @Override
    HotelWktRepository getHotelWktRepository() {
        return context.getBean(PostgresHotelWktRepository)
    }

    @Memoized
    @Override
    DeliveryDriverJsonRepository getDeliveryDriverJsonRepository() {
        return context.getBean(PostgresDeliveryDriverJsonRepository)
    }

    @Memoized
    @Override
    DeliveryDriverWktRepository getDeliveryDriverWktRepository() {
        return context.getBean(PostgresDeliveryDriverWktRepository)
    }

    @Memoized
    @Override
    DeliveryDriverWktGeographyRepository getDeliveryDriverWktGeographyRepository() {
        return context.getBean(PostgresDeliveryDriverWktGeographyRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo", "io.micronaut.data.r2dbc.postgres")
    }

    @Override
    Map<String, String> getProperties() {
        def props = getDataSourceProperties("postgresgeospatial")
        ServiceLoader.load(TestPropertyProviderFactory).stream()
                .forEach {
                    props.putAll(it.get().create(props, this.class).get())
                }
        return props
    }

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def prefix = 'r2dbc.datasources.' + dataSourceName
        return [
                (prefix + '.db-type')                          : dbType(),
                (prefix + '.schema-generate')                  : schemaGenerate().name(),
                (prefix + '.dialect')                          : dialect().name(),
                (prefix + '.packages')                         : packages(),
                (prefix + '.connectTimeout')                   : Duration.ofMinutes(1).toString(),
                (prefix + '.statementTimeout')                 : Duration.ofMinutes(1).toString(),
                (prefix + '.lockTimeout')                      : Duration.ofMinutes(1).toString(),
                "test-resources.containers.postgres.image-name": "postgis/postgis",
                "test-resources.containers.postgres.image-tag" : "17-3.5"
        ] as Map<String, String>
    }

    void "test crud when json conversion used on geography type"() {
        given:
        GeographyEntityJson entity = new GeographyEntityJson()
        entity.setPoint(createPoint(1))
        entity.setMultiPoint(createMultiPoint(1))
        entity.setLineString(createLineString(1))
        entity.setMultiLineString(createMultiLineString(1))
        entity.setPolygon(createPolygon(1))
        entity.setMultiPolygon(createMultiPolygon(1))
        entity.setGeometryCollection(createGeometryCollection(3))

        when:
        GeographyEntityJson savedEntity = getGeographyEntityJsonRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeographyEntityJson> foundEntity = getGeographyEntityJsonRepository().findById(savedEntity.id)

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
        getGeographyEntityJsonRepository().update(entity)
        foundEntity = getGeographyEntityJsonRepository().findById(savedEntity.id)

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
        getGeographyEntityJsonRepository().update(entity)
        foundEntity = getGeographyEntityJsonRepository().findById(savedEntity.id)

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

    void "test crud when wkt conversion used on geography type"() {
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
}
