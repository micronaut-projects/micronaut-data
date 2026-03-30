package io.micronaut.data.jdbc.postgres

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class PostgresGeoSpec extends AbstractGeoSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    GeometryEntityJsonRepository getGeometryEntityJsonRepository() {
        return context.getBean(PostgresGeometryEntityJsonRepository)
    }

    @Memoized
    @Override
    GeometryEntityWktRepository getGeometryEntityWktRepository() {
        return context.getBean(PostgresGeometryEntityWktRepository)
    }

    @Memoized
    PostgresGeographyEntityJsonRepository getGeographyEntityJsonRepository() {
        return context.getBean(PostgresGeographyEntityJsonRepository)
    }

    @Memoized
    PostgresGeographyEntityWktRepository getGeographyEntityWktRepository() {
        return context.getBean(PostgresGeographyEntityWktRepository)
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
