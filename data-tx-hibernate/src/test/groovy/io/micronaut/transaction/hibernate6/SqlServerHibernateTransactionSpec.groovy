package io.micronaut.transaction.hibernate6


import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.tests.TestResourcesDatabaseTestPropertyProvider

class SqlServerHibernateTransactionSpec extends HibernateTransactionSpec implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.SQL_SERVER
    }

    @Override
    Map<String, String> getProperties() {
        return TestResourcesDatabaseTestPropertyProvider.super.getProperties() + [
                "datasources.default.name"                       : "mymssqldb",
                'jpa.default.properties.hibernate.hbm2ddl.auto'  : 'create-drop',
                'jpa.default.properties.hibernate.dialect'       : 'org.hibernate.dialect.SQLServerDialect',
                'test-resources.containers.mssql.accept-license' : 'true'
        ]
    }

    @Override
    boolean supportsReadOnlyFlag() {
        return true
    }

}
