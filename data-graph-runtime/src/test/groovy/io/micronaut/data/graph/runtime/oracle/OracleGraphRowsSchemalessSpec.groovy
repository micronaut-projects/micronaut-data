package io.micronaut.data.graph.runtime.oracle

import io.micronaut.data.graph.runtime.GraphNode
import spock.lang.Specification

/**
 * Checks how the JSON column of schemaless properties is read next to the declared columns of a vertex.
 */
class OracleGraphRowsSchemalessSpec extends Specification {

    private static final List<String> COLUMNS = ["n__ID_", "n__LABEL_", "n__price", "n__DYN_"]

    void "schemaless properties are read with the declared columns"() {
        when:
        def node = (GraphNode) OracleGraphRows.decode(COLUMNS, [["Phone", "Gadget", 500, '{"colour":"black","removed":null}']])[0].get("n")

        then:
        node.properties() == [price: 500, colour: "black"]
    }

    void "a declared column wins over a schemaless property of the same name, also when it is null"() {
        when:
        def rows = OracleGraphRows.decode(COLUMNS, [
            ["Phone", "Gadget", 500, '{"colour":"black","price":1}'],
            ["Cable", "Gadget", null, '{"price":2}']
        ])

        then:
        ((GraphNode) rows[0].get("n")).properties() == [price: 500, colour: "black"]
        ((GraphNode) rows[1].get("n")).properties() == [price: null]
    }
}
