package io.micronaut.data.r2dbc.mysql

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

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
}
