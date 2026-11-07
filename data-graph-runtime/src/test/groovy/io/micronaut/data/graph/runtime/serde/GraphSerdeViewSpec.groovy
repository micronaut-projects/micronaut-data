package io.micronaut.data.graph.runtime.serde

import io.micronaut.context.ApplicationContext
import io.micronaut.data.graph.runtime.GraphNode
import io.micronaut.data.graph.runtime.entities.Track
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Checks what an entity reads from a node or a relationship, independently of a database.
 */
class GraphSerdeViewSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    GraphSerde serde = context.getBean(GraphSerde)
    RuntimeEntityRegistry entities = context.getBean(RuntimeEntityRegistry)

    void "properties named by a naming strategy or a mapping are read under their persisted names"() {
        when:
        def track = serde.readNode(entities.getEntity(Track), node('Track', [track_name: 'Idioteque', play_count: 7, seconds: 309]), null)

        then:
        track == new Track('Idioteque', 7, 309)
    }

    void "a node property that is not the persisted name of a property is not read into it"() {
        when:
        def track = serde.readNode(entities.getEntity(Track), node('Track', [track_name: 'Idioteque', playCount: 7, length: 309]), null)

        then:
        track == new Track('Idioteque', null, null)
    }

    private static GraphNode node(String label, Map<String, Object> properties) {
        return new GraphNode('1', [label], properties, 1L)
    }
}
