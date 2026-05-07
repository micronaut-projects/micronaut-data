package io.micronaut.data.jdbc.mariadb

import groovy.transform.Memoized
import io.micronaut.data.jdbc.mysql.MySqlGeometryEntityJsonRepository
import io.micronaut.data.jdbc.mysql.MySqlGeometryEntityWktRepository
import io.micronaut.data.jdbc.mysql.MySqlHotelRepository
import io.micronaut.data.jdbc.mysql.MySqlSchoolRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class MariaGeoSpec extends AbstractGeoSpec implements MariaTestPropertyProvider {

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
    HotelRepository getHotelRepository() {
        return context.getBean(MySqlHotelRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }
}
