package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class OracleXEGeoSpec extends AbstractGeoSpec implements OracleTestPropertyProvider {

    @Memoized
    @Override
    GeoEntityRepository getGeoEntityRepository() {
        return context.getBean(OracleXEGeoEntityRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities")
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
                "test-resources.containers.oracle.image-name": "gvenzl/oracle-free",
                "test-resources.containers.oracle.image-tag" : "latest"
        ] as Map<String, String>
    }
}
