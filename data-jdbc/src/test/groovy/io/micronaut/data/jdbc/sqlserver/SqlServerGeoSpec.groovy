package io.micronaut.data.jdbc.sqlserver

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class SqlServerGeoSpec extends AbstractGeoSpec implements MSSQLTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityRepository getGeoEntityRepository() {
        return context.getBean(MSGeoEntityRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities")
    }
}
