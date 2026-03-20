package io.micronaut.data.jdbc.mysql

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class MySqlGeoSpec extends AbstractGeoSpec implements MySQLTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityRepository getGeoEntityRepository() {
        return context.getBean(MySqlGeoEntityRepository)
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
