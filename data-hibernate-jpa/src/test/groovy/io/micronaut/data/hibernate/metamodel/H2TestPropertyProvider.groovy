package io.micronaut.data.hibernate.metamodel

import io.micronaut.test.support.TestPropertyProvider

trait H2TestPropertyProvider implements TestPropertyProvider {

    List<String> packages() {
        def currentClassPackage = getClass().package.name
        return Arrays.asList(currentClassPackage, "io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities")
    }

    boolean shouldAddDefaultDbProperties() {
        return true
    }

    Map<String, String> getProperties() {
        return shouldAddDefaultDbProperties() ? getH2DataSourceProperties("default") : [:]
    }

    Map<String, String> getH2DataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        Map<String, String> map = [
                (prefix + '.url')            : "jdbc:h2:mem:${dataSourceName};LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
                (prefix + '.dialect')        : 'h2',
                (prefix + '.username')       : '',
                (prefix + '.password')       : '',
                (prefix + '.packages')       : packages(),
                (prefix + '.driverClassName'): "org.h2.Driver"
        ] as Map<String, String>
        map.put("jpa.default.properties.hibernate.hbm2ddl.auto", "create-drop")
        return map
    }

}
