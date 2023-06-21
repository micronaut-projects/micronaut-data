package io.micronaut.data.r2dbc.mariadb

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.TestResourcesDatabaseTestPropertyProvider

trait MariaDbTestPropertyProvider implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.MYSQL
    }

    @Override
    String dbType() {
        return "mariadb"
    }


    Map<String, String> getDataSourceProperties(String dataSourceName) {
        return super.getDataSourceProperties(dataSourceName) + ["test-resources.containers.mysql.image-name": "mariadb:10.6"]
    }

}
