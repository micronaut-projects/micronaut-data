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
}
