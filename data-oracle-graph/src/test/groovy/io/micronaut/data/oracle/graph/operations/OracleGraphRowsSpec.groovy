package io.micronaut.data.oracle.graph.operations

import io.micronaut.data.graph.runtime.GraphEdge
import io.micronaut.data.graph.runtime.oracle.OracleGraphRows
import io.micronaut.data.graph.runtime.GraphNode
import io.micronaut.data.graph.runtime.GraphPathValue
import spock.lang.Specification

class OracleGraphRowsSpec extends Specification {

    void "expand collection parameters"() {
        when:
        def expanded = OracleGraphRows.expand("SELECT * FROM t WHERE a IN (?) AND b = ? AND c = '?'", [[1, 2, 3], "x"])

        then:
        expanded.query() == "SELECT * FROM t WHERE a IN (?, ?, ?) AND b = ? AND c = '?'"
        expanded.parameters() == [1, 2, 3, "x"]

        when:
        expanded = OracleGraphRows.expand("SELECT * FROM t WHERE a IN (?)", [[]])

        then:
        expanded.query() == "SELECT * FROM t WHERE a IN (NULL)"
        expanded.parameters() == []
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
}
