package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.data.r2dbc.AbstractR2dbcMultitenancySpec
import io.micronaut.data.r2dbc.CleanupTestResourcesDatabaseTestPropertyProvider
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.test.support.TestPropertyProviderFactory
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Shared

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

@TestResourcesScope("multitenancy-r2-oracle")
class OracleXEMultitenancySpec extends AbstractR2dbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

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

    void "schema generation restores the session schema of the pooled connection"() {
        given:
        ApplicationContext context = ApplicationContext.run(testResourcesProperties() + getDataSourceProperties('default') + [
                'r2dbc.datasources.default.schema-generate'     : 'CREATE',
                'r2dbc.datasources.default.schema-generate-name': 'gen_restore_r2',
                'r2dbc.datasources.default.options.initialSize' : '1',
                'r2dbc.datasources.default.options.maxSize'     : '1'
        ], Environment.TEST)
        ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

        when:
        List<String> schemaAndUser = Mono.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(connection.createStatement("SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'), SYS_CONTEXT('USERENV', 'SESSION_USER') FROM DUAL").execute())
                        .flatMap(result -> result.map((row, metadata) -> [row.get(0, String), row.get(1, String)]))
                        .next(),
                connection -> connection.close()
        ).block()

        then:
        schemaAndUser[1]
        schemaAndUser[0] == schemaAndUser[1]

        cleanup:
        context?.close()
    }

    /**
     * Schema multitenancy on Oracle creates users and tables in other schemas, so the application
     * user needs system privileges. Test Resources starts Oracle Free with the same password for
     * SYSTEM and the application user, which lets the spec grant them over JDBC.
     */
    private void grantSchemaPrivileges() {
        ApplicationContext context = ApplicationContext.run(testResourcesProperties() + getDataSourceProperties('default') + [
                'r2dbc.datasources.default.schema-generate': 'NONE'
        ], Environment.TEST)
        try {
            ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(context.getRequiredProperty('r2dbc.datasources.default.url', String))
            String username = context.getRequiredProperty('r2dbc.datasources.default.username', String)
            String password = context.getRequiredProperty('r2dbc.datasources.default.password', String)
            String jdbcUrl = "jdbc:oracle:thin:@${options.getRequiredValue(ConnectionFactoryOptions.HOST)}:${options.getRequiredValue(ConnectionFactoryOptions.PORT)}/${options.getRequiredValue(ConnectionFactoryOptions.DATABASE)}"
            try (Connection connection = DriverManager.getConnection(jdbcUrl, 'system', password);
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
