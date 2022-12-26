package io.micronaut.data.r2dbc.h2


import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider

trait H2TestPropertyProvider implements TestPropertyProvider {

    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE
    }

    List<String> packages() {
        def currentClassPackage = getClass().package.name
        return Arrays.asList(currentClassPackage, "io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities")
    }

    Map<String, String> getProperties() {
        return getH2DataSourceProperties("default")
    }

    Map<String, String> getH2DataSourceProperties(String dataSourceName) {
        def prefix = 'r2dbc.datasources.' + dataSourceName
        return [
                (prefix + '.url')            : "r2dbc:h2:mem:///${dataSourceName};DB_CLOSE_DELAY=10",
                (prefix + '.schema-generate'): schemaGenerate(),
                (prefix + '.dialect')        : 'h2',
                (prefix + '.username')       : '',
                (prefix + '.password')       : '',
                (prefix + '.packages')       : packages(),
        ] as Map<String, String>
    }

}
