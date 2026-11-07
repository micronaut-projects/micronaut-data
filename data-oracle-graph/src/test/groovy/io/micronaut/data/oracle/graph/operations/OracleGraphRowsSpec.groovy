package io.micronaut.data.oracle.graph.operations

import io.micronaut.data.graph.runtime.GraphEdge
import io.micronaut.data.graph.runtime.GraphNode
import io.micronaut.data.graph.runtime.GraphPathValue
import spock.lang.Specification

class OracleGraphRowsSpec extends Specification {

    void "expand collection parameters"() {
        when:
        def expanded = OracleGraphRows.expand("SELECT * FROM t WHERE a IN (?) AND b = ? AND c = '?'", [p0: [1, 2, 3], p1: "x"])

        then:
        expanded.query() == "SELECT * FROM t WHERE a IN (?, ?, ?) AND b = ? AND c = '?'"
        expanded.parameters() == [1, 2, 3, "x"]

        when:
        expanded = OracleGraphRows.expand("SELECT * FROM t WHERE a IN (?)", [p0: []])

        then:
        expanded.query() == "SELECT * FROM t WHERE a IN (NULL)"
        expanded.parameters() == []
    }

    void "bind named parameters by occurrence"() {
        when:
        def expanded = OracleGraphRows.expand("SELECT * FROM v\$t WHERE b = \$b AND a IN (\$a) AND c = \$b AND d = '\$d'", [a: [1, 2], b: "x"])

        then:
        expanded.query() == "SELECT * FROM v\$t WHERE b = ? AND a IN (?, ?) AND c = ? AND d = '\$d'"
        expanded.parameters() == ["x", 1, 2, "x"]

        when:
        OracleGraphRows.expand("SELECT * FROM t WHERE a = \$missing", [a: 1])

        then:
        thrown(IllegalStateException)
    }

    void "placeholders in comments are not parameters"() {
        when:
        def expanded = OracleGraphRows.expand("SELECT * FROM t /* why? */ WHERE a = \$a -- \$b why?\n AND b = 1", [a: 1])

        then:
        expanded.query() == "SELECT * FROM t /* why? */ WHERE a = ? -- \$b why?\n AND b = 1"
        expanded.parameters() == [1]
    }

    void "an empty collection cannot be bound to NOT IN"() {
        when:
        OracleGraphRows.expand("SELECT * FROM t WHERE a NOT IN (?)", [p0: []])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("NOT IN")
    }

    void "decode a relationship entity with its identifier and properties"() {
        when:
        def records = OracleGraphRows.decode(
            ["s__ID_", "s__LABEL_", "t__ID_", "t__LABEL_", "r__ID_", "r__amount", "r__TYPE_"],
            [[1, "Account", 2, "Account", 7, 500, "TRANSFERS"]]
        )

        then:
        records[0].keys() == ["f", "t", "r"]
        with((GraphEdge) records[0].get("r")) {
            id() == 7
            type() == "TRANSFERS"
            startId() == 1
            endId() == 2
            properties() == [amount: 500]
        }
    }

    void "a relationship leaves out its null properties"() {
        when:
        def records = OracleGraphRows.decode(
            ["s__ID_", "s__LABEL_", "t__ID_", "t__LABEL_", "r__ID_", "r__amount", "r__note", "r__TYPE_"],
            [[1, "Account", 2, "Account", 7, null, "x", "TRANSFERS"]]
        )

        then:
        ((GraphEdge) records[0].get("r")).properties() == [note: "x"]
    }

    void "an undirected relationship is read with its stored direction"() {
        when:
        def records = OracleGraphRows.decode(
            ["s__ID_", "s__LABEL_", "s__name", "t__ID_", "t__LABEL_", "t__name", "r__TYPE_", "r__FORWARD_"],
            [[2, "Account", "Bob", 1, "Account", "Alice", "TRANSFERS", 0],
             [1, "Account", "Alice", 2, "Account", "Bob", "TRANSFERS", 1]]
        )

        then: "matched from the destination, the ends swap"
        ((GraphNode) records[0].get("f")).properties().name == "Alice"
        ((GraphNode) records[0].get("t")).properties().name == "Bob"
        with((GraphEdge) records[0].get("r")) {
            startId() == 1
            endId() == 2
            properties() == [:]
        }

        and: "matched from the source, they stay"
        ((GraphNode) records[1].get("f")).properties().name == "Alice"
        ((GraphEdge) records[1].get("r")).startId() == 1
    }

    void "decode nodes from aliased columns"() {
        when:
        def records = OracleGraphRows.decode(
            ["n__ID_", "n__title", "n__LABEL_", "n__released"],
            [["The Matrix", "The Matrix", "Movie", 1999]]
        )

        then:
        records.size() == 1
        records[0].keys() == ["n"]
        with((GraphNode) records[0].get("n")) {
            id() == "The Matrix"
            labels() == ["Movie"]
            properties() == [title: "The Matrix", released: 1999]
        }
    }

    void "decode projections as plain columns"() {
        when:
        def records = OracleGraphRows.decode(["title", "released"], [["Speed", 1994]])

        then:
        records[0].asMap() == [title: "Speed", released: 1994]
    }

    void "decode relationships with their endpoints"() {
        when:
        def records = OracleGraphRows.decode(
            ["s__ID_", "s__LABEL_", "s__name", "t__ID_", "t__LABEL_", "t__name", "r__TYPE_"],
            [[1, "Account", "Alice", 2, "Account", "Bob", "TRANSFERS"]]
        )

        then:
        records[0].keys() == ["f", "t", "r"]
        ((GraphNode) records[0].get("f")).properties().name == "Alice"
        ((GraphNode) records[0].get("t")).properties().name == "Bob"
        with((GraphEdge) records[0].get("r")) {
            type() == "TRANSFERS"
            startId() == 1
            endId() == 2
        }
    }

    void "decode paths from one row per step"() {
        given:
        def columns = ["p__M_", "p__I_", "v1__ID_", "v1__LABEL_", "v1__name", "v2__ID_", "v2__LABEL_", "v2__name", "x__TYPE_"]
        def rows = [
            [7, 2, 1, "Friend", "Fred", 2, "Friend", "Barney", "FRIENDS"],
            [7, 4, 2, "Friend", "Barney", 3, "Friend", "Joe", "FRIENDS"],
            [9, 2, 1, "Friend", "Fred", 4, "Friend", "Wilma", "FRIENDS"],
            [9, 4, 4, "Friend", "Wilma", 3, "Friend", "Joe", "FRIENDS"]
        ]

        when:
        def records = OracleGraphRows.decode(columns, rows)

        then:
        records.size() == 2
        records*.get("p").every { it instanceof GraphPathValue }
        ((GraphPathValue) records[0].get("p")).nodes()*.properties()*.name == ["Fred", "Barney", "Joe"]
        ((GraphPathValue) records[1].get("p")).nodes()*.properties()*.name == ["Fred", "Wilma", "Joe"]
        ((GraphPathValue) records[0].get("p")).relationships()*.type() == ["FRIENDS", "FRIENDS"]
        ((GraphPathValue) records[0].get("p")).relationships()*.startId() == [1, 2]
    }

    void "decode the type of each step of a path over several relationship types"() {
        given:
        def columns = ["p__M_", "p__I_", "v1__ID_", "v1__LABEL_", "v1__name", "v2__ID_", "v2__LABEL_", "v2__name", "x__TYPE_"]
        def rows = [
            [1, 2, 1, "Person", "Ada", 2, "Person", "Grace", "MENTORED_BY"],
            [1, 4, 2, "Person", "Grace", 3, "Person", "Barbara", "FOLLOWS"]
        ]

        when:
        def path = (GraphPathValue) OracleGraphRows.decode(columns, rows)[0].get("p")

        then:
        path.nodes()*.id() == [1, 2, 3]
        path.relationships()*.type() == ["MENTORED_BY", "FOLLOWS"]
        path.relationships()*.startId() == [1, 2]
        path.relationships()*.endId() == [2, 3]
    }

    void "decode a path whose vertices have no identifier column"() {
        given: "Fred -> Barney -> Fred -> Wilma: the cycle closes on Fred, the other vertices stay distinct"
        def columns = ["p__M_", "p__I_", "v1__LABEL_", "v1__name", "v2__LABEL_", "v2__name", "x__TYPE_"]
        def rows = [
            [1, 2, "Friend", "Fred", "Friend", "Barney", "FRIENDS"],
            [1, 4, "Friend", "Barney", "Friend", "Fred", "FRIENDS"],
            [1, 6, "Friend", "Fred", "Friend", "Wilma", "FRIENDS"],
            [2, 2, "Friend", "Wilma", "Friend", "Wilma", "FRIENDS"]
        ]

        when:
        def records = OracleGraphRows.decode(columns, rows)
        def path = (GraphPathValue) records[0].get("p")
        def loop = (GraphPathValue) records[1].get("p")

        then:
        path.nodes()*.properties()*.name == ["Fred", "Barney", "Fred", "Wilma"]
        path.nodes()*.id() == [0, 1, 0, 2]
        path.relationships()*.startId() == [0, 1, 0]
        path.relationships()*.endId() == [1, 0, 2]

        and: "a step from a vertex to itself is one node"
        loop.nodes()*.properties()*.name == ["Wilma", "Wilma"]
        loop.nodes()*.id().unique().size() == 1
        loop.relationships()[0].startId() == loop.relationships()[0].endId()
    }

    void "decode a path whose identifier column is null"() {
        given:
        def columns = ["p__M_", "p__I_", "v1__ID_", "v1__LABEL_", "v1__name", "v2__ID_", "v2__LABEL_", "v2__name", "x__TYPE_"]
        def rows = [[1, 2, null, "Friend", "Fred", null, "Friend", "Barney", "FRIENDS"]]

        when:
        def path = (GraphPathValue) OracleGraphRows.decode(columns, rows)[0].get("p")

        then:
        path.nodes()*.properties()*.name == ["Fred", "Barney"]
        path.nodes()*.id().unique().size() == 2
        path.relationships()[0].startId() != path.relationships()[0].endId()
    }
}
