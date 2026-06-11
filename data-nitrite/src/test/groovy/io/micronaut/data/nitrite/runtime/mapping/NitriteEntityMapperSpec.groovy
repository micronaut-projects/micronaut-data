package io.micronaut.data.nitrite.runtime.mapping

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteEntityMapperSpec extends Specification {

    @Inject ConversionService conversionService
    @Inject ObjectMapper objectMapper
    @Inject Nitrite nitrite
    @Inject RuntimeEntityRegistry runtimeEntityRegistry

    def "test cyclic association handling in NitriteEntityMapper"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, nitrite.getConfig().nitriteMapper(), runtimeEntityRegistry)

        def parent = new CyclicEntity(name: "Parent")
        parent.id = 1L
        def child = new CyclicEntity(name: "Child", parent: parent)
        child.id = 2L
        
        // Create the cycle
        parent.parent = child

        when: "converting to document with cycle"
        def doc = mapper.toDocument(parent)

        then: "child is embedded, but its parent ref is just the ID to avoid infinite recursion"
        doc != null
        def childDoc = doc.get("parent", Document)
        childDoc.get("name") == "Child"
        childDoc.get("parent") == 1L // ID of parent
    }

    def "test nested POJO mapping and naming strategies"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, nitrite.getConfig().nitriteMapper(), runtimeEntityRegistry)
        
        def pojo = new NestedPojo(camelCaseField: "camel", snake_case_field: "snake")
        def holder = new PojoHolder(id: 1L, nested: pojo)

        when: "serialize to document"
        def doc = mapper.toDocument(holder)
        
        then:
        doc.get("nested") instanceof Map
        def nestedMap = doc.get("nested") as Map
        nestedMap["camelCaseField"] == "camel"
        nestedMap["snake_case_field"] == "snake"

        when: "deserialize from document"
        def deserialized = mapper.fromDocument(doc, PojoHolder)

        then:
        deserialized.nested != null
        deserialized.nested.camelCaseField == "camel"
        deserialized.nested.snake_case_field == "snake"

        when: "deserialize with alternate naming"
        def rawMap = [
            camel_case_field: "camelConverted", // snake case in map, field is camel
            snakeCaseField: "snakeConverted" // camel case in map, field is snake
        ]
        def modifiedDoc = Document.createDocument("id", 2L).put("nested", rawMap)
        def altered = mapper.fromDocument(modifiedDoc, PojoHolder)

        then: "fallback lookups correctly resolve the fields"
        altered.nested != null
        altered.nested.camelCaseField == "camelConverted"
        altered.nested.snake_case_field == "snakeConverted"
    }

}

@MappedEntity
class CyclicEntity {
    @Id @GeneratedValue Long id
    String name
    
    @Relation(Relation.Kind.EMBEDDED)
    CyclicEntity parent
}

@Introspected
class NestedPojo {
    String camelCaseField
    String snake_case_field
}

@MappedEntity
class PojoHolder {
    @Id @GeneratedValue Long id
    NestedPojo nested
}
