package io.micronaut.transaction.hibernate6

import io.micronaut.test.support.TestPropertyProvider

trait H2Properties implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return [
                "datasources.default.name"                     : "mydb",
                "datasources.default.schema-generate"          : "create-drop",
                "datasources.default.dialect"                  : "h2",
                "datasources.default.driver-class-name"        : "org.h2.Driver",
                "datasources.default.username"                 : "sa",
                "datasources.default.password"                 : "",
                "datasources.default.url"                      : "jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
                "jpa.default.properties.hibernate.hbm2ddl.auto": "create-drop"
        ]
    }
}
