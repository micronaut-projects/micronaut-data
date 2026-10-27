package io.micronaut.data.graph.runtime.age

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
}
