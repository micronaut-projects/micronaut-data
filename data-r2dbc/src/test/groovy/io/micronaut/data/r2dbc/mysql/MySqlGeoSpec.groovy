package io.micronaut.data.r2dbc.mysql

import groovy.transform.Memoized
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
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Mono

class MySqlGeoSpec extends AbstractGeoSpec implements MySqlTestPropertyProvider {

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
        def coordinates = Mono.usingWhen(context.getBean(ConnectionFactory).create(),
            { Connection connection ->
                Mono.from(connection.createStatement("SELECT ST_Longitude(location), ST_Latitude(location) FROM delivery_driver_wkt WHERE id = ?")
                    .bind(0, saved.id())
                    .execute())
                    .flatMap { result ->
                        Mono.from(result.map { row, metadata ->
                            [row.get(0, Double), row.get(1, Double)]
                        })
                    }
            },
            { Connection connection -> connection.close() }
        ).block()

        then:
        Math.abs(coordinates[0] - location.x()) < 1e-9
        Math.abs(coordinates[1] - location.y()) < 1e-9
    }
}
