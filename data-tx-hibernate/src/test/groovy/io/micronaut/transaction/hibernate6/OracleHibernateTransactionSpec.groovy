package io.micronaut.transaction.hibernate6

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.tests.TestResourcesDatabaseTestPropertyProvider

class OracleHibernateTransactionSpec extends HibernateTransactionSpec implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.ORACLE
    }

    @Override
    Map<String, String> getProperties() {
        return TestResourcesDatabaseTestPropertyProvider.super.getProperties() + [
                "datasources.default.name"                     : "myoracledb",
                'jpa.default.properties.hibernate.hbm2ddl.auto': 'create-drop',
                'jpa.default.properties.hibernate.dialect'     : 'org.hibernate.dialect.OracleDialect'
        ]
    }

    @Override
    boolean supportsReadOnlyFlag() {
        return true
    }

}
