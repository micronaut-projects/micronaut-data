package io.micronaut.data.graph.runtime.age

import io.micronaut.data.graph.runtime.GraphEdge
import io.micronaut.data.graph.runtime.GraphNode
import io.micronaut.data.graph.runtime.GraphPathValue
import spock.lang.Specification


class AgtypeSpec extends Specification {

    void "parse scalars"() {
        expect:
        Agtype.parse(text) == value

        where:
        text                | value
        null                | null
        'null'              | null
        '1'                 | 1L
        '-42'               | -42L
        '1.5'               | 1.5d
        '1.5::float'        | 1.5d
        '3::numeric'        | new BigDecimal("3")
        'true'              | true
        '"a \\"b\\" \\n c"' | 'a "b" \n c'
        '"\\u00e9"'         | 'é'
    }

    void "parse lists and maps"() {
        expect:
        Agtype.parse('[1, "two", [3]]') == [1L, "two", [3L]]
        Agtype.parse('{"a": 1, "b": {"c": null}}') == [a: 1L, b: [c: null]]
        Agtype.parse('[]') == []
        Agtype.parse('{}') == [:]
    }

    void "parse a vertex"() {
        when:
        def node = Agtype.parse('{"id": 844424930131969, "label": "Person", "properties": {"name": "Keanu", "born": 1964}}::vertex')

        then:
        node instanceof GraphNode
        node.id() == 844424930131969L
        node.labels() == ["Person"]
        node.properties() == [name: "Keanu", born: 1964L]
    }

    void "parse an edge"() {
        when:
        def edge = Agtype.parse('{"id": 1125899906842625, "label": "ACTED_IN", "end_id": 844424930131970, "start_id": 844424930131969, "properties": {"roles": ["Neo"]}}::edge')

        then:
        edge instanceof GraphEdge
        edge.type() == "ACTED_IN"
        edge.startId() == 844424930131969L
        edge.endId() == 844424930131970L
        edge.properties() == [roles: ["Neo"]]
    }

    void "parse a path and vertices nested in collections"() {
        when:
        def path = Agtype.parse('[{"id": 1, "label": "Friend", "properties": {"name": "Fred"}}::vertex, {"id": 3, "label": "FRIENDS", "end_id": 2, "start_id": 1, "properties": {}}::edge, {"id": 2, "label": "Friend", "properties": {"name": "Joe"}}::vertex]::path')
        def collected = Agtype.parse('[{"rel": {"id": 3, "label": "R", "end_id": 2, "start_id": 1, "properties": {}}::edge, "node": {"id": 2, "label": "Friend", "properties": {}}::vertex}]')

        then:
        path instanceof GraphPathValue
        path.nodes()*.properties()*.name == ["Fred", "Joe"]
        path.relationships()*.type() == ["FRIENDS"]
        collected[0].rel instanceof GraphEdge
        collected[0].node instanceof GraphNode
    }

    void "reject malformed values"() {
        when:
        Agtype.parse('{"a": }')

        then:
        thrown(IllegalArgumentException)
    }
}
