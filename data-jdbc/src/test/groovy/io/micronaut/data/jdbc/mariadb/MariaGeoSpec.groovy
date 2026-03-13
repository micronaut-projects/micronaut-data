package io.micronaut.data.jdbc.mariadb

import groovy.transform.Memoized
import io.micronaut.data.jdbc.mysql.MySqlGeoEntityRepository
import io.micronaut.data.tck.repositories.GeoEntityRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class MariaGeoSpec extends AbstractGeoSpec implements MariaTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityRepository getGeoEntityRepository() {
        return context.getBean(MySqlGeoEntityRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities")
    }
}
