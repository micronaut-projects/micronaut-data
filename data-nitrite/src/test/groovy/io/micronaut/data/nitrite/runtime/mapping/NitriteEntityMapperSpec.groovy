package io.micronaut.data.nitrite.runtime.mapping

import io.micronaut.data.nitrite.model.MapEntity
import io.micronaut.data.nitrite.model.NestedPojo
import io.micronaut.data.nitrite.model.CompositeFkChild
import io.micronaut.data.nitrite.model.CompositeFkParent
import io.micronaut.data.nitrite.model.CompositeIdEntity
import io.micronaut.data.nitrite.model.CompositeIdCollectionChild
import io.micronaut.data.nitrite.model.MappedNumericCompositeIdEntity
import io.micronaut.data.nitrite.model.NitriteComplexValue
import io.micronaut.data.nitrite.runtime.CountingOperationsHelper
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.core.annotation.Creator
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.model.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteId
import org.dizitart.no2.common.tuples.Pair
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteEntityMapperSpec extends Specification {
    @Inject ConversionService conversionService
    @Inject ObjectMapper objectMapper
    @Inject Nitrite nitrite
    @Inject RuntimeEntityRegistry runtimeEntityRegistry

    def "a numeric equality narrows to the property's declared type when the conversion service declines"() {
        given: "a conversion service that converts nothing, so the explicit narrowing switch is used"
        def decliningConversionService = new ConversionService() {
            @Override
            def <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
                Optional.empty()
            }

            @Override
            def <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
                false
            }
        }
        def mapper = new NitriteEntityMapper(decliningConversionService, objectMapper, runtimeEntityRegistry)
        def entity = runtimeEntityRegistry.getEntity(Person)
        def expected = mapper.eqWithNumericCoercion(entity, "age", 10 as Integer, "age").toString()

        expect: "every incoming numeric width collapses onto the Integer-typed filter"
        mapper.eqWithNumericCoercion(entity, "age", value, "age").toString() == expected

        where:
        value << [10L, (short) 10, (byte) 10, 10.0f, 10.0d, 10 as Integer]
    }

    def "an eq filter on a widened numeric property is a single indexable equality, not an or-fan-out"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def entity = runtimeEntityRegistry.getEntity(Person)

        expect: "with no entity, or a field it does not declare, one equality is emitted"
        !mapper.eqWithNumericCoercion(null, "age", 10L, "age").toString().contains("||")
        !mapper.eqWithNumericCoercion(entity, "unknown", 10L, "unknown").toString().contains("||")

        and: "a non-numeric value is unaffected"
        !mapper.eqWithNumericCoercion(entity, "name", "hello", "name").toString().contains("||")

        and: "a null value becomes an is-null test rather than an equality"
        mapper.eqWithNumericCoercion(entity, "name", null, "name").toString() !=
            mapper.eqWithNumericCoercion(entity, "name", "hello", "name").toString()
    }


    def "test cyclic association handling in NitriteEntityMapper"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

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

    def "constructor-based bidirectional associations are cycle-safe during hydration"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def parentCollection = nitrite.getCollection("ConstructorCycleParent")
        def childCollection = nitrite.getCollection("ConstructorCycleChild")
        parentCollection.insert(Document.createDocument("id", "parent-1").put("child", "child-1"))
        childCollection.insert(Document.createDocument("id", "child-1").put("parent", "parent-1"))
        def helper = new CountingOperationsHelper()
                .register(ConstructorCycleParent, parentCollection)
                .register(ConstructorCycleChild, childCollection)
        mapper.setHelper(helper)

        when:
        def parent = mapper.fromDocument(
                Document.createDocument("id", "parent-1").put("child", "child-1"),
                ConstructorCycleParent)

        then:
        noExceptionThrown()
        parent.child.parent.is(parent)
    }

    def "test nested POJO mapping and naming strategies"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

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
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

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

    def "test serializeForDocument fallback paths for store values"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

        when: "an id map holds a Serde-incompatible (but Serializable) POJO and a raw array"
        def id = new CustomId(info: [bad: new PlainSerializable(), arr: [1, 2, 3] as int[]])
        def doc = mapper.toDocument(new CustomIdEntity(id: id))

        then:
        def infoDoc = (doc.get("id") as Document).get("info") as Document

        and: "the Serde-incompatible value is stored as-is (writeValueAsString throws, caught)"
        infoDoc.get("bad") instanceof PlainSerializable

        and: "a raw array (java type) is stored as-is, Serde skipped"
        infoDoc.get("arr") as int[] == [1, 2, 3] as int[]
    }

    def "test Map conversion with nested POJOs"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

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
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

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

    def "composite identity values are not treated as a scalar identity"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def composite = new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "record")

        expect:
        mapper.toFilterValue(composite).is(composite)
        mapper.toNitriteFilterValue(composite).is(composite)
    }

    def "a core mapped value without identity remains a filter value"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def value = new NitriteComplexValue("key", "data")

        expect:
        mapper.toFilterValue(value).is(value)
        mapper.toNitriteFilterValue(value).is(value)
    }

    def "constructor association hydration performs one lookup per association"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def parentCollection = nitrite.getCollection("CompositeFkParent")
        parentCollection.insert(Document.createDocument("id", "parent-1")
                .put("tenant_id", "tenant-a")
                .put("ref_id", 42L))
        def helper = new CountingOperationsHelper().register(CompositeFkParent, parentCollection)
        mapper.setHelper(helper)
        def childDocument = Document.createDocument("id", "child-1")
                .put("name", "child-a")
                .put("parent", "parent-1")

        when:
        def child = mapper.fromDocument(childDocument, CompositeFkChild)

        then:
        helper.lookupCount(CompositeFkParent) == 1
        child.parent.tenantId == "tenant-a"
        child.parent.refId == 42L
    }

    def "mapping multiple roots eagerly resolves each to-one association independently"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def parentCollection = nitrite.getCollection("CompositeFkParent")
        parentCollection.insert(Document.createDocument("id", "parent-1")
                .put("tenant_id", "tenant-a")
                .put("ref_id", 42L))
        def helper = new CountingOperationsHelper().register(CompositeFkParent, parentCollection)
        mapper.setHelper(helper)

        when:
        def first = mapper.fromDocument(
                Document.createDocument("id", "child-1").put("name", "child-a").put("parent", "parent-1"),
                CompositeFkChild)
        def second = mapper.fromDocument(
                Document.createDocument("id", "child-2").put("name", "child-b").put("parent", "parent-1"),
                CompositeFkChild)

        then:
        helper.lookupCount(CompositeFkParent) == 2
        first.parent.is(second.parent) == false
        first.parent.id == second.parent.id
    }

    def "test NitriteTypeRegistry get missing entry"() {
        expect:
        NitriteTypeRegistry.get(Object) == null
    }

    def "a joined association fetched as a Document or List of Documents hydrates directly"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

        when: "association is populated as a single Document"
        def docWithDoc = Document.createDocument("id", "1")
            .put("child", Document.createDocument("id", "2"))
        def rootSingle = mapper.fromDocument(docWithDoc, ConstructorCycleParent)

        then:
        rootSingle.child != null
        rootSingle.child.id == "2"

        when: "association is populated as a List of Documents"
        def docWithList = Document.createDocument("id", "1")
            .put("children", [
                Document.createDocument("id", "c1"),
                Document.createDocument("id", "c2")
            ])
        def rootList = mapper.fromDocument(docWithList, UnembeddedParent)

        then:
        rootList.children != null
        rootList.children.size() == 2
        rootList.children[0].id == "c1"
        rootList.children[1].id == "c2"
    }

    def "composite join columns are resolved by property name and absent for an unknown property"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)

        expect: "the mapped association reports both of its @JoinColumn entries"
        mapper.getCompositeJoinColumns(CompositeIdCollectionChild, "parent")*.localName() ==
            ["parent_tenant_id", "parent_ref_id"]

        and: "a property the entity does not have resolves to no join columns rather than failing"
        mapper.getCompositeJoinColumns(CompositeIdCollectionChild, "noSuchProperty").isEmpty()
    }

    def "a composite identity key is read from a Map by persisted name, by property name, and from a Document"() {
        given: "an entity whose @MappedProperty names differ from its property names"
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def meta = mapper.getOrBuildMeta(MappedNumericCompositeIdEntity)
        def expected = mapper.idEqualsFilter(meta,
            new MappedNumericCompositeIdEntity(42L, 7, null)).toString()

        expect: "every key shape resolves to the same filter as the entity itself"
        mapper.idEqualsFilter(meta, key).toString() == expected

        where:
        key << [
            [tenant_key: 42L, sequence_no: 7],
            [tenantId: 42L, sequence: 7],
            Document.createDocument("tenant_key", 42L).put("sequence_no", 7),
            Document.createDocument("tenantId", 42L).put("sequence", 7)
        ]
    }

    def "a composite identity filter matches nothing when the key is absent or incomplete"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def meta = mapper.getOrBuildMeta(CompositeIdEntity)
        def document = Document.createDocument("tenant_id", "tenant-a").put("ref_id", "ref-1")
        def element = Pair.pair(NitriteId.newId(), document)

        expect: "a null key never degrades into a filter that matches the whole collection"
        !mapper.idEqualsFilter(meta, null).apply(element)

        and: "half an identity identifies nothing rather than matching on the half that is set"
        !mapper.idEqualsFilter(meta, new CompositeIdEntity(tenantId: "tenant-a")).apply(element)

        and: "the complete identity still matches"
        mapper.idEqualsFilter(meta,
            new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1")).apply(element)
    }

    def "a composite identity key of the wrong type is rejected rather than silently read as null"() {
        given:
        def mapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def meta = mapper.getOrBuildMeta(CompositeIdEntity)

        when:
        mapper.idEqualsFilter(meta, "not-a-composite-id")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(CompositeIdEntity.name)
    }
}

@MappedEntity
class UnembeddedParent {
    @Id String id

    @Relation(Relation.Kind.ONE_TO_MANY)
    List<UnembeddedChild> children
}

@MappedEntity
class UnembeddedChild {
    @Id String id
}

@MappedEntity
class CyclicEntity {
    @Id @GeneratedValue Long id
    String name

    @Relation(Relation.Kind.EMBEDDED)
    CyclicEntity parent
}

@MappedEntity
class ConstructorCycleParent {
    @Id String id

    @Relation(Relation.Kind.MANY_TO_ONE)
    ConstructorCycleChild child

    @Creator
    ConstructorCycleParent(String id, ConstructorCycleChild child) {
        this.id = id
        this.child = child
    }
}

@MappedEntity
class ConstructorCycleChild {
    @Id String id

    @Relation(Relation.Kind.MANY_TO_ONE)
    ConstructorCycleParent parent

    @Creator
    ConstructorCycleChild(String id, ConstructorCycleParent parent) {
        this.id = id
        this.parent = parent
    }
}

@MappedEntity
class PojoHolder {
    @Id @GeneratedValue Long id
    NestedPojo nested
}

@Introspected
@Serdeable
class CustomId {
    Map<String, Object> info
}

@MappedEntity
class CustomIdEntity {
    @Id CustomId id
}

@Serdeable
class CustomSerializable implements Serializable {
    String val
    @Override String toString() { val }
}

class NonSerdeable {
}

// Serializable so Nitrite can store it, but no @Serdeable/@Introspected so Serde
// serialization throws -> serializeForDocument falls into its catch and returns it as-is.
class PlainSerializable implements Serializable {
    String data = "x"
}
