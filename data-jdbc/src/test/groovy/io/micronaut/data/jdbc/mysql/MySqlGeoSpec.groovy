package io.micronaut.data.jdbc.mysql

import groovy.transform.Memoized
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.model.geo.Point
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverWkt
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class MySqlGeoSpec extends AbstractGeoSpec implements MySQLTestPropertyProvider {

    @Memoized
    @Override
    GeometryEntityJsonRepository getGeometryEntityJsonRepository() {
        return context.getBean(MySqlGeometryEntityJsonRepository)
    }

    @Memoized
    @Override
    GeometryEntityWktRepository getGeometryEntityWktRepository() {
        return context.getBean(MySqlGeometryEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(MySqlSchoolRepository)
    }

    @Memoized
    @Override
    HotelJsonRepository getHotelJsonRepository() {
        return context.getBean(MySqlHotelJsonRepository)
    }

    @Memoized
    @Override
    HotelWktRepository getHotelWktRepository() {
        return context.getBean(MySqlHotelWktRepository)
    }

    @Memoized
    @Override
    DeliveryDriverJsonRepository getDeliveryDriverJsonRepository() {
        return context.getBean(MySqlDeliveryDriverJsonRepository)
    }

    @Memoized
    @Override
    DeliveryDriverWktRepository getDeliveryDriverWktRepository() {
        return context.getBean(MySqlDeliveryDriverWktRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }

    void "test WKT uses database longitude latitude order"() {
        given:
        def repository = getDeliveryDriverWktRepository()
        def location = new Point(-73.9757d, 40.7554d)
        def saved = repository.save(new DeliveryDriverWkt("New York Driver", DeliveryDriverWkt.Status.AVAILABLE, location))

        when:
        def coordinates = context.getBean(ConnectionOperations).executeRead { status ->
            status.connection.prepareStatement("SELECT ST_Longitude(location), ST_Latitude(location) FROM delivery_driver_wkt WHERE id = ?").withCloseable { statement ->
                statement.setLong(1, saved.id())
                statement.executeQuery().withCloseable { result ->
                    assert result.next()
                    [result.getDouble(1), result.getDouble(2)]
                }
            }
        }

        then:
        Math.abs(coordinates[0] - location.x()) < 1e-9
        Math.abs(coordinates[1] - location.y()) < 1e-9
    }
}
