package io.micronaut.data.age

import io.micronaut.context.ApplicationContext
import io.micronaut.data.age.features.Account
import io.micronaut.data.age.features.AccountRepository
import io.micronaut.data.age.features.ReportingAccountRepository
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

/**
 * Checks that the repositories of each data source write to the graph configured for it. The second data source is a
 * bean named {@code reportingDb}, whose entry under {@code micronaut.data.age.graphs} configuration binds as
 * {@code reporting-db}.
 */
class AgeDataSourceGraphsSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context

    void setupSpec() {
        // The second data source connects to the database that Test Resources starts for the default one
        def database = ApplicationContext.run()
        Map<String, Object> properties
        try {
            properties = [
                'spec.name'                            : 'AgeDataSourceGraphsSpec',
                'reporting.url'                        : database.getRequiredProperty('datasources.default.url', String),
                'reporting.username'                   : database.getRequiredProperty('datasources.default.username', String),
                'reporting.password'                   : database.getRequiredProperty('datasources.default.password', String),
                'micronaut.data.age.graph'             : 'main_graph',
                'micronaut.data.age.graphs.reportingDb': 'reporting_graph'
            ]
        } finally {
            database.close()
        }
        context = ApplicationContext.run(properties)
        sql("CREATE EXTENSION IF NOT EXISTS age")
    }

    void cleanupSpec() {
        if (context != null) {
            dropGraph("main_graph")
            dropGraph("reporting_graph")
        }
    }

    void "each data source writes to its own graph"() {
        given:
        def accounts = context.getBean(AccountRepository)
        def reportingAccounts = context.getBean(ReportingAccountRepository)

        when: "an account is saved through each data source"
        accounts.save(new Account("main@example.com", "UK", "London", "main"))
        reportingAccounts.save(new Account("reporting@example.com", "FR", "Paris", "reporting"))

        then: "each lands in the graph of its data source"
        accounts.findAll()*.email == ["main@example.com"]
        reportingAccounts.findAll()*.email == ["reporting@example.com"]
        count("main_graph") == 1
        count("reporting_graph") == 1
    }

    private long count(String graph) {
        sql("LOAD 'age'")
        withConnection { Connection connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery("SELECT count(*) FROM \"" + graph + "\".\"Account\"").withCloseable { it.next(); it.getLong(1) }
            }
        }
    }

    private void dropGraph(String graph) {
        withConnection { Connection connection ->
            connection.prepareStatement("SELECT ag_catalog.drop_graph(name, true) FROM ag_catalog.ag_graph WHERE name = ?").withCloseable { statement ->
                statement.setString(1, graph)
                statement.execute()
            }
        }
    }

    private void sql(String statement) {
        withConnection { Connection connection -> connection.createStatement().withCloseable { it.execute(statement) } }
    }

    private <T> T withConnection(Closure<T> work) {
        DataSource dataSource = context.getBean(DataSource, Qualifiers.byName("default"))
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { Connection connection ->
            connection.autoCommit = true
            work(connection)
        }
    }
}
