package io.micronaut.data.jdbc.h2

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.GeoEntityRepository
import io.micronaut.data.tck.tests.AbstractGeoSpec

class H2GeoSpec extends AbstractGeoSpec implements H2TestPropertyProvider {

    private static final String H2_URL_PROPERTIES = 'LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;INIT=' +
            'CREATE ALIAS IF NOT EXISTS H2GIS_SPATIAL FOR "org.h2gis.functions.factory.H2GISFunctions.load"\\;CALL H2GIS_SPATIAL()'

    @Memoized
    @Override
    GeoEntityRepository getGeoEntityRepository() {
        return context.getBean(H2GeoEntityRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities")
    }

    @Override
    Map<String, String> getH2DataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        return [
                (prefix + '.url')            : "jdbc:h2:mem:${dataSourceName};${H2_URL_PROPERTIES}",
                (prefix + '.schema-generate'): schemaGenerate(),
                (prefix + '.dialect')        : 'h2',
                (prefix + '.username')       : '',
                (prefix + '.password')       : '',
                (prefix + '.packages')       : packages(),
                (prefix + '.driverClassName'): "org.h2.Driver"
        ] as Map<String, String>
    }
}
