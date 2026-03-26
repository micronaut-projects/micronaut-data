package io.micronaut.data.jdbc.mysql

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityJsonRepository
import io.micronaut.data.tck.repositories.GeoEntityWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class MySqlGeoSpec extends AbstractGeoSpec implements MySQLTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityJsonRepository getGeoEntityJsonRepository() {
        return context.getBean(MySqlGeoEntityJsonRepository)
    }

    @Memoized
    @Override
    GeoEntityWktRepository getGeoEntityWktRepository() {
        return context.getBean(MySqlGeoEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(MySqlSchoolRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }
}
