package io.micronaut.data.oracle.graph

import io.micronaut.context.annotation.Property
import io.micronaut.data.oracle.graph.schemaless.Gadget
import io.micronaut.data.oracle.graph.schemaless.GadgetGraph
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Schemaless properties of Oracle vertex tables, stored in a JSON column.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.oracle.graph.schemaless")
class OracleSchemalessSpec extends Specification {

    @Inject
    GadgetGraph gadgetGraph

    void cleanup() {
        gadgetGraph.deleteAll()
    }

    void "schemaless properties are stored in a JSON column"() {
        given:
        def phone = new Gadget("Phone", 500)
        phone.set("colour", "black")
        phone.set("cameras", [12, 48])
        phone.set("waterproof", true)

        when:
        gadgetGraph.save(phone)
        gadgetGraph.save(new Gadget("Cable", 10))
        def found = gadgetGraph.findById("Phone").get()

        then:
        found.price == 500
        found.get("colour") == "black"
        found.get("cameras") == [12L, 48L]
        found.get("waterproof") == true
        gadgetGraph.findById("Cable").get().dynamicAttributes().isEmpty()
        gadgetGraph.findByPriceGreaterThanOrderByName(100)*.name == ["Phone"]

        when: "a property set to null is removed and another is added"
        found.set("colour", null)
        found.set("storage", 256)
        gadgetGraph.update(found)
        def updated = gadgetGraph.findById("Phone").get()

        then:
        updated.dynamicAttributes() == [cameras: [12L, 48L], waterproof: true, storage: 256L]
    }
}
