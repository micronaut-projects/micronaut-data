package io.micronaut.data.graph.runtime.age

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class AgeConfigurationSpec extends Specification {

    void "the graph of a data source falls back to the default graph"() {
        given:
        def configuration = new AgeConfiguration()

        expect:
        configuration.graph == AgeConfiguration.DEFAULT_GRAPH
        configuration.graphFor("default") == AgeConfiguration.DEFAULT_GRAPH
        !configuration.createExtension
        configuration.createGraph

        when:
        configuration.graph = "movies"
        configuration.graphs = [reporting: "analytics"]
        configuration.createExtension = true
        configuration.createGraph = false

        then:
        configuration.graphFor("reporting") == "analytics"
        configuration.graphFor("default") == "movies"
        configuration.graphs == [reporting: "analytics"]
        configuration.createExtension
        !configuration.createGraph
    }

    void "the graph of a data source with a camel case name is found however configuration binds it"() {
        given: "configuration binds the keys of the graphs in hyphenated form"
        def context = ApplicationContext.builder()
            .properties('micronaut.data.age.graphs.reportingDb': 'analytics')
            .propertySources(PropertySource.of('env', ['MICRONAUT_DATA_AGE_GRAPHS_AUDIT_DB': 'audit'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE))
            .start()
        def configuration = context.getBean(AgeConfiguration)

        expect:
        configuration.graphs.keySet() == ['reporting-db', 'audit-db'] as Set
        configuration.graphFor("reportingDb") == "analytics"
        configuration.graphFor("reporting-db") == "analytics"
        configuration.graphFor("auditDb") == "audit"
        configuration.graphFor("default") == AgeConfiguration.DEFAULT_GRAPH

        cleanup:
        context?.close()
    }

    void "the graph of a tenant is found however configuration binds its id"() {
        given: "configuration binds the keys of the tenant graphs in hyphenated, lower case form"
        def context = ApplicationContext.builder()
            .properties(
                'micronaut.data.age.tenant-graph-prefix': 'tenant_',
                'micronaut.data.age.tenant-graphs.tenantA': 'graph_a',
                'micronaut.data.age.tenant-graphs.ACME': 'graph_acme')
            .start()
        def configuration = context.getBean(AgeConfiguration)

        expect:
        configuration.tenantGraphs.keySet() == ['tenant-a', 'acme'] as Set
        configuration.tenantGraph("tenantA") == "graph_a"
        configuration.tenantGraph("tenant-a") == "graph_a"
        configuration.tenantGraph("ACME") == "graph_acme"
        configuration.tenantGraph("acme") == "graph_acme"
        configuration.tenantGraph("globex") == "tenant_globex"

        cleanup:
        context?.close()
    }

    void "a tenant cannot share the graph of something else"() {
        given:
        def configuration = new AgeConfiguration(graph: "movies", tenantGraphs: [acme: "acme_movies", globex: "shared", initech: "shared", legacy: "movies", pub: "public"])

        when:
        configuration.tenantGraph(tenant)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(graph)

        where:
        tenant        | graph
        "movies"      | "movies"
        "legacy"      | "movies"
        "public"      | "public"
        "pub"         | "public"
        "ag_catalog"  | "ag_catalog"
        "pg_catalog"  | "pg_catalog"
        "acme_movies" | "acme_movies"
        "shared"      | "shared"
        "globex"      | "shared"
        "initech"     | "shared"
    }

    void "a prefix keeps a tenant id away from the names of other things"() {
        given:
        def configuration = new AgeConfiguration(graph: "movies", tenantGraphPrefix: "tenant_", tenantGraphs: [acme: "acme_movies"])

        expect:
        configuration.tenantGraph("movies") == "tenant_movies"
        configuration.tenantGraph("public") == "tenant_public"
        configuration.tenantGraph("acme") == "acme_movies"
    }

    void "the graphs of tenants are not created unless enabled"() {
        given:
        def configuration = new AgeConfiguration()

        expect:
        !configuration.createTenantGraphs

        when:
        configuration.createTenantGraphs = true

        then:
        configuration.createTenantGraphs
    }
}
