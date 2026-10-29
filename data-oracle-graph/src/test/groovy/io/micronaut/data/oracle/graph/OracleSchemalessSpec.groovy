package io.micronaut.data.oracle.graph

import io.micronaut.context.annotation.Property
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.graph.model.criteria.GraphCriteria
import io.micronaut.data.oracle.graph.schemaless.Gadget
import io.micronaut.data.oracle.graph.schemaless.GadgetGraph
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
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

    void "criteria read schemaless properties as values of their type"() {
        given:
        def phone = new Gadget("Phone", 500)
        phone.set("storage", 100)
        phone.set("waterproof", true)
        phone.set("maker.name", "Acme")
        def watch = new Gadget("Watch", 200)
        watch.set("storage", 20)
        watch.set("waterproof", false)
        gadgetGraph.saveAll([phone, watch])

        expect: "numbers compare as numbers, where as strings \"100\" sorts before \"30\""
        gadgetGraph.findAll(where { root, cb -> cb.greaterThan(GraphCriteria.dynamic(root, "storage", Integer), 30) })*.name == ["Phone"]
        gadgetGraph.findAll(where { root, cb -> cb.isTrue(GraphCriteria.dynamic(root, "waterproof", Boolean)) })*.name == ["Phone"]

        and: "a name holding a dot is one key rather than a nested path"
        gadgetGraph.findAll(where { root, cb -> cb.equal(GraphCriteria.dynamic(root, "maker.name", String), "Acme") })*.name == ["Phone"]
    }

    void "a schemaless property named like a column is rejected when saved"() {
        given:
        def phone = new Gadget("Phone", 500)
        phone.set("price", 1)

        when:
        gadgetGraph.save(phone)

        then:
        def e = thrown(DataAccessException)
        e.message == "Dynamic attribute [price] of [Gadget] has the name of a declared property"
        gadgetGraph.count() == 0
    }

    private static PredicateSpecification<Gadget> where(Closure<?> predicate) {
        return predicate as PredicateSpecification<Gadget>
    }
}
