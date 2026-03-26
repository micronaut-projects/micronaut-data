package io.micronaut.data.jdbc.postgres

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityJsonRepository
import io.micronaut.data.tck.repositories.GeoEntityWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class PostgresGeoSpec extends AbstractGeoSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityJsonRepository getGeoEntityJsonRepository() {
        return context.getBean(PostgresGeoEntityJsonRepository)
    }

    @Memoized
    @Override
    GeoEntityWktRepository getGeoEntityWktRepository() {
        return context.getBean(PostgresGeoEntityWktRepository)
    }

    @Memoized
    @Override
    SchoolRepository getSchoolRepository() {
        return context.getBean(PostgresSchoolRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.geo")
    }

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        return [
                (prefix + '.db-type')                        : dbType(),
                (prefix + '.schema-generate')                : schemaGenerate(),
                (prefix + '.dialect')                        : dialect(),
                (prefix + '.packages')                       : packages(),
                (prefix + '.enabled')                        : dataSourceEnabled(dataSourceName),
                "test-resources.containers.postgres.image-name": "postgis/postgis",
                "test-resources.containers.postgres.image-tag" : "latest"
        ] as Map<String, String>
    }
}
