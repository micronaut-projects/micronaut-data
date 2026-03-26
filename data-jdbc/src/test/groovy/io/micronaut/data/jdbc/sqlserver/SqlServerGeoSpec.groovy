package io.micronaut.data.jdbc.sqlserver

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityJsonRepository
import io.micronaut.data.tck.repositories.GeoEntityWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class SqlServerGeoSpec extends AbstractGeoSpec implements MSSQLTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityJsonRepository getGeoEntityJsonRepository() {
        return context.getBean(MSGeoEntityJsonRepository)
    }

    @Memoized
    @Override
    GeoEntityWktRepository getGeoEntityWktRepository() {
        return context.getBean(MSGeoEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(MSSchoolRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }

    @Override
    protected boolean supportsGeometryJsonConversion() {
        // SqlServer doesn't have built-in functions for conversion
        // between json and internal geometry/geography data types
        return false
    }
}
