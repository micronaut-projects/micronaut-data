package io.micronaut.data.r2dbc.mariadb

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.mysql.MySqlGeometryEntityJsonRepository
import io.micronaut.data.r2dbc.mysql.MySqlGeometryEntityWktRepository
import io.micronaut.data.r2dbc.mysql.MySqlSchoolRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class MariaDbGeoSpec extends AbstractGeoSpec implements MariaDbTestPropertyProvider {

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

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }
}
