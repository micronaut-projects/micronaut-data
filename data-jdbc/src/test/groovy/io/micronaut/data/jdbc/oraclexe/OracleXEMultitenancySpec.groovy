package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.data.jdbc.AbstractJdbcMultitenancySpec
import io.micronaut.data.jdbc.CleanupTestResourcesDatabaseTestPropertyProvider
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.test.support.TestPropertyProviderFactory
import spock.lang.Shared

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

@TestResourcesScope("multitenancy-oracle")
class OracleXEMultitenancySpec extends AbstractJdbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

    @Shared
    boolean schemaPrivilegesGranted

    def setup() {
        if (!schemaPrivilegesGranted) {
            grantSchemaPrivileges()
            schemaPrivilegesGranted = true
        }
    }

    @Override
    Map<String, String> getExtraProperties() {
        return [bookRepositoryClass: OracleXEBookRepository.name]
    }

    @Override
    Map<String, String> getDataSourceProperties() {
        return [
                'db-type'        : 'oracle',
                'schema-generate': 'CREATE_DROP',
                'dialect'        : 'ORACLE',
                'packages'       : 'io.micronaut.data.tck.entities'
        ]
    }

    /**
     * Schema multitenancy on Oracle creates users and tables in other schemas, so the application
     * user needs system privileges. Test Resources starts Oracle Free with the same password for
     * SYSTEM and the application user, which lets the spec grant them.
     */
    private void grantSchemaPrivileges() {
        ApplicationContext context = ApplicationContext.run(testResourcesProperties() + getDataSourceProperties('default') + [
                'datasources.default.schema-generate': 'NONE'
        ], Environment.TEST)
        try {
            String url = context.getRequiredProperty('datasources.default.url', String)
            String username = context.getRequiredProperty('datasources.default.username', String)
            String password = context.getRequiredProperty('datasources.default.password', String)
            try (Connection connection = DriverManager.getConnection(url, 'system', password);
                 Statement statement = connection.createStatement()) {
                statement.execute("""GRANT CREATE USER, CREATE ANY TABLE, ALTER ANY TABLE, DROP ANY TABLE, CREATE ANY INDEX,
                    CREATE ANY SEQUENCE, DROP ANY SEQUENCE, SELECT ANY SEQUENCE, SELECT ANY TABLE, INSERT ANY TABLE,
                    UPDATE ANY TABLE, DELETE ANY TABLE, LOCK ANY TABLE TO $username""".toString())
            }
        } finally {
            context.close()
        }
    }

    private Map<String, String> testResourcesProperties() {
        Map<String, String> properties = new HashMap<>(extraProperties)
        ServiceLoader.load(TestPropertyProviderFactory).stream()
                .forEach { properties.putAll(it.get().create([:], this.class).get()) }
        return properties
    }
}
