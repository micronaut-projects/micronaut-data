package io.micronaut.data.oracle.graph.r2dbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.data.oracle.graph.r2dbc.wallet.JdbcWalletGraph
import io.micronaut.data.oracle.graph.r2dbc.wallet.Wallet
import io.micronaut.data.oracle.graph.r2dbc.wallet.WalletGraph
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Runs the JDBC and R2DBC Oracle graph modules in one application, on one database. The data source and the connection
 * factory have different names: two of the same name clash on the beans each is qualified by, such as its
 * ConnectionOperations.
 */
class OracleGraphJdbcAndR2dbcSpec extends Specification {

    private static final String TEST_RESOURCES_ENABLED = "micronaut.test.resources.enabled"

    @Shared
    @AutoCleanup
    ApplicationContext context

    void setupSpec() {
        // Test resources would also look for an R2DBC connection factory of the name of each data source, so the second
        // context does without them, on the database they resolved the connection factory to
        Map<String, Object> properties
        try (ApplicationContext r2dbc = ApplicationContext.run()) {
            def url = r2dbc.getRequiredProperty('r2dbc.datasources.default.url', String)
            def username = r2dbc.getRequiredProperty('r2dbc.datasources.default.username', String)
            def password = r2dbc.getRequiredProperty('r2dbc.datasources.default.password', String)
            properties = [
                'r2dbc.datasources.default.url'        : url,
                'r2dbc.datasources.default.username'   : username,
                'r2dbc.datasources.default.password'   : password,
                'datasources.jdbc.url'                 : url.replace('r2dbc:oracle://', 'jdbc:oracle:thin:@'),
                'datasources.jdbc.username'            : username,
                'datasources.jdbc.password'            : password,
                'datasources.jdbc.driver-class-name'   : 'oracle.jdbc.OracleDriver',
                'datasources.jdbc.dialect'             : 'ORACLE'
            ]
        }
        System.setProperty(TEST_RESOURCES_ENABLED, "false")
        context = ApplicationContext.run(properties)
        def connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource, Qualifiers.byName("jdbc"))).getConnection()
        try {
            for (String statement in [
                "DROP PROPERTY GRAPH IF EXISTS wallet_graph",
                "DROP TABLE IF EXISTS payments",
                "DROP TABLE IF EXISTS wallets",
                "CREATE TABLE wallets (id NUMBER PRIMARY KEY, owner VARCHAR2(100), opened TIMESTAMP, audited TIMESTAMP)",
                "CREATE TABLE payments (id NUMBER PRIMARY KEY, src NUMBER REFERENCES wallets (id), dst NUMBER REFERENCES wallets (id), amount NUMBER(10, 2))",
                """CREATE PROPERTY GRAPH wallet_graph
                   VERTEX TABLES (wallets KEY (id) LABEL Wallet PROPERTIES (id, owner, opened, audited))
                   EDGE TABLES (payments KEY (id)
                       SOURCE KEY (src) REFERENCES wallets (id)
                       DESTINATION KEY (dst) REFERENCES wallets (id)
                       LABEL PAID PROPERTIES (id, amount))"""]) {
                connection.createStatement().withCloseable { it.execute(statement) }
            }
        } finally {
            connection.close()
        }
    }

    void cleanupSpec() {
        System.clearProperty(TEST_RESOURCES_ENABLED)
    }

    void "the attribute converters start with the vector converters of both modules"() {
        expect: "the vector converters of JDBC and R2DBC do not clash"
        context.getBeansOfType(AttributeConverter).size() > 0
    }

    void "a repository of each module reads what the other wrote"() {
        given:
        def reactive = context.getBean(WalletGraph)
        def blocking = context.getBean(JdbcWalletGraph)

        when:
        reactive.save(new Wallet(10L, "Judy", [])).block()
        blocking.save(new Wallet(11L, "Ken", []))

        then:
        blocking.findById(10L).get().owner() == "Judy"
        reactive.findById(11L).block().owner() == "Ken"
    }
}
