package io.micronaut.data.graph.runtime.operations

import spock.lang.Specification

/**
 * Checks the order in which the nodes of a batch are linked.
 */
class PendingLinksSpec extends Specification {

    private final PendingLinks<String> links = new PendingLinks<>()

    void "a node is linked after the nodes it relates to, and once"() {
        given:
        def (a, b, c) = [new Object(), new Object(), new Object()]
        add(a, "a", b, c)
        add(b, "b", c)
        add(c, "c")

        expect:
        links.take(a).toList() == ["c", "b", "a"]
        links.take(a).toList() == []
        links.take(c).toList() == []
    }

    void "nodes that relate to each other are linked once"() {
        given:
        def (a, b) = [new Object(), new Object()]
        add(a, "a", b, a)
        add(b, "b", a)

        expect:
        links.take(a).toList() == ["b", "a"]
    }

    void "a node that the walk has not reached can be taken by a step"() {
        given:
        def (a, b, c) = [new Object(), new Object(), new Object()]
        add(a, "a", b, c)
        add(b, "b")
        add(c, "c")
        def walk = links.take(a)

        expect:
        walk.next() == "b"
        links.take(c).toList() == ["c"]
        walk.toList() == ["a"]
    }

    void "a long chain is walked without recursion"() {
        given:
        def nodes = (0..<200_000).collect { new Object() }
        nodes.eachWithIndex { node, i -> add(node, String.valueOf(i), *(i + 1 < nodes.size() ? [nodes[i + 1]] : [])) }

        when:
        def order = links.take(nodes[0]).toList()

        then:
        order.size() == nodes.size()
        order.first() == "199999"
        order.last() == "0"
    }

    private void add(Object instance, String link, Object... related) {
        links.add(instance, { related.toList() }, link)
    }
}
