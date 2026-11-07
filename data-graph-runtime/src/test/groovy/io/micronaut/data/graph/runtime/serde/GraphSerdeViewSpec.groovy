package io.micronaut.data.graph.runtime.serde

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder
import io.micronaut.data.graph.runtime.GraphEdge
import io.micronaut.data.graph.runtime.GraphEntityMapper
import io.micronaut.data.graph.runtime.GraphNode
import io.micronaut.data.graph.runtime.GraphRecord
import io.micronaut.data.graph.runtime.entities.Item
import io.micronaut.data.graph.runtime.entities.Placement
import io.micronaut.data.graph.runtime.entities.Shelf
import io.micronaut.data.graph.runtime.entities.Track
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.function.Function

/**
 * Checks what an entity reads from a node or a relationship, independently of a database.
 */
class GraphSerdeViewSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    GraphSerde serde = context.getBean(GraphSerde)
    RuntimeEntityRegistry entities = context.getBean(RuntimeEntityRegistry)
    GraphEntityMapper mapper = new GraphEntityMapper(entities, ConversionService.SHARED, Function.identity(), serde)

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

    void "a fetched node and the target of a fetched relationship keep their schemaless data"() {
        given:
        def item = new GraphNode('2', ['Item', 'Fragile'], [name: 'vase', colour: 'blue'], 2L)
        def edge = new GraphEdge('3', 'PLACES', '1', '2', [position: 4], 3L)
        def row = new GraphRecord(['j_items', 'j_placements'], [[item], [[(CypherQueryBuilder.REL_KEY): edge, (CypherQueryBuilder.NODE_KEY): item]]])

        when:
        Shelf shelf = mapper.readNode(entities.getEntity(Shelf), node('Shelf', [name: 'top']), row)

        then:
        shelf.items() == [new Item('vase', [colour: 'blue'], ['Fragile'])]
        shelf.placements() == [new Placement(4, new Item('vase', [colour: 'blue'], ['Fragile']))]

        when:
        Placement placement = mapper.readRelationshipEntity(entities.getEntity(Placement), edge, item)

        then:
        placement == new Placement(4, new Item('vase', [colour: 'blue'], ['Fragile']))
    }

    private static GraphNode node(String label, Map<String, Object> properties) {
        return new GraphNode('1', [label], properties, 1L)
    }
}
