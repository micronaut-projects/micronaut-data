package io.micronaut.data.nitrite.runtime.mapping

import io.micronaut.data.nitrite.model.MapEntity
import io.micronaut.data.nitrite.model.NestedPojo
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
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

    def "test NitriteEntityMapper misc gaps"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, nitrite.getConfig().nitriteMapper(), runtimeEntityRegistry)

        expect:
        mapper.isSimpleType(String)
        !mapper.isSimpleType(NestedPojo)
        mapper.toDocument(null) == null

        when: "using a custom ID with a map to trigger toDocumentValue and serializeForDocument"
        def custom = new CustomSerializable(val: "custom-val")
        def id = new CustomId(info: [foo: custom])
        def doc = mapper.toDocument(new CustomIdEntity(id: id))

        then: "the nested map should have been converted to a Document and its entry serialized"
        doc != null
        doc.get("id") instanceof Document
        def idDoc = doc.get("id") as Document
        idDoc.get("info") instanceof Document
        def infoDoc = idDoc.get("info") as Document
        infoDoc.get("foo") instanceof Document
        (infoDoc.get("foo") as Document).get("val") == "custom-val"
    }

    def "test Map conversion with nested POJOs"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, nitrite.getConfig().nitriteMapper(), runtimeEntityRegistry)

        def map = [
            "key1": new NestedPojo(camelCaseField: "v1", snake_case_field: "s1"),
            "key2": new NestedPojo(camelCaseField: "v2", snake_case_field: "s2")
        ]
        def entity = new MapEntity(data: map)

        when: "converting to document"
        def doc = mapper.toDocument(entity)

        then: "Map is converted to Document, and nested POJOs are recursively converted to Documents"
        doc.get("data") instanceof Document
        def dataDoc = doc.get("data") as Document
        dataDoc.get("key1") instanceof Document
        (dataDoc.get("key1") as Document).get("camelCaseField") == "v1"

        when: "converting back from document"
        def result = mapper.fromDocument(doc, MapEntity)

        then:
        result.data != null
        result.data["key1"] instanceof NestedPojo
        result.data["key1"].camelCaseField == "v1"
        result.data["key2"].camelCaseField == "v2"
    }

    def "test toNitriteFilterValue coverage"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, nitrite.getConfig().nitriteMapper(), runtimeEntityRegistry)

        expect:
        mapper.toNitriteFilterValue(null) == null

        when:
        def doc = Document.createDocument()
        then:
        mapper.toNitriteFilterValue(doc).is(doc)

        when:
        def res1 = mapper.toNitriteFilterValue("str")
        then:
        res1 == "str"

        when:
        def pojo = new NestedPojo(camelCaseField: "v1")
        def result = mapper.toNitriteFilterValue(pojo)
        then:
        result instanceof Document
        result.get("camelCaseField") == "v1"

        when: "handling non-serdeable object to trigger exception path in serializeForDocument"
        def nonSerdeable = new NonSerdeable()
        def resNonSerde = mapper.toNitriteFilterValue(nonSerdeable)
        then: "it falls back to returning the object as-is"
        resNonSerde.is(nonSerdeable)

        when: "POJO with MappedEntity (CyclicEntity)"
        def entity = new CyclicEntity(name: "Test")
        entity.id = 123L
        def res2 = mapper.toNitriteFilterValue(entity)
        then:
        res2 == 123L

        when: "POJO with MappedEntity but idValue is null"
        def entityNoId = new CyclicEntity(name: "NoId")
        def res3 = mapper.toNitriteFilterValue(entityNoId)
        then:
        res3.is(entityNoId)
    }

    def "test NitriteTypeRegistry get missing entry"() {
        expect:
        NitriteTypeRegistry.get(Object) == null
    }

}

@MappedEntity
class CyclicEntity {
    @Id @GeneratedValue Long id
    String name
    
    @Relation(Relation.Kind.EMBEDDED)
    CyclicEntity parent
}

@MappedEntity
class PojoHolder {
    @Id @GeneratedValue Long id
    NestedPojo nested
}

@Introspected
@io.micronaut.serde.annotation.Serdeable
class CustomId {
    Map<String, Object> info
}

@MappedEntity
class CustomIdEntity {
    @Id CustomId id
}

@io.micronaut.serde.annotation.Serdeable
class CustomSerializable implements Serializable {
    String val
    @Override String toString() { val }
}

class NonSerdeable {
}
