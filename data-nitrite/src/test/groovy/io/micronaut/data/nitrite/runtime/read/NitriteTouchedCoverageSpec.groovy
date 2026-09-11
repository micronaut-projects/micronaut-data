package io.micronaut.data.nitrite.runtime.read

import io.micronaut.core.annotation.Nullable
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.JoinColumn
import io.micronaut.data.annotation.sql.JoinColumns
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.runtime.ValueConverter
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.Tuple
import jakarta.persistence.TupleElement
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteId
import org.dizitart.no2.common.tuples.Pair
import org.dizitart.no2.filters.Filter
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteTouchedCoverageSpec extends Specification {

    @Inject
    ConversionService conversionService

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry

    private NitriteEntityMapper entityMapper

    def setup() {
        entityMapper = new NitriteEntityMapper(conversionService, null, runtimeEntityRegistry)
    }

    void "tuple typed access covers conversion metadata and validation branches"() {
        given:
        Tuple tuple = new NitriteTuple(
            new ValueConverter(conversionService),
            ["7", null, "text"] as Object[],
            [number: 0],
            ["number"],
            [Integer])

        expect: "typed access converts by alias and position"
        tuple.get("number", Integer) == 7
        tuple.get(0, Long) == 7L

        and: "declared, null-fallback and runtime-inferred element types are retained"
        tuple.elements*.getAlias() == ["number", null, null]
        tuple.elements*.getJavaType() == [Integer, Object, String]

        when:
        tuple.get(new TupleElement<Object>() {
            @Override
            Class<? extends Object> getJavaType() {
                Object
            }

            @Override
            @Nullable
            String getAlias() {
                "unknown"
            }
        })

        then:
        thrown(IllegalArgumentException)

        when:
        tuple.get(-1)

        then:
        thrown(IllegalArgumentException)
    }

    void "projection and property resolution cover persisted-name fallbacks"() {
        given:
        def persistentEntity = runtimeEntityRegistry.getEntity(GapReverseParent)
        def projectionMapper = new CollectionProjectionMapper(
            new ValueConverter(conversionService), entityMapper)
        def document = Document.createDocument("name", "fallback value")

        expect: "the alias-aware forwarding overload falls back to the Java field name"
        projectionMapper.mapDocument(
            document,
            ["name"],
            ["resultName"],
            persistentEntity,
            String,
            false) == "fallback value"

        and: "properties resolve through Java names, mapped identity names and mapped regular names"
        entityMapper.findPropertyByNameOrPersistedName(persistentEntity, "name").name == "name"
        entityMapper.findPropertyByNameOrPersistedName(persistentEntity, "id").name == "id"
        entityMapper.findPropertyByNameOrPersistedName(persistentEntity, "display_name").name == "name"
        entityMapper.findPropertyByNameOrPersistedName(persistentEntity, "missing") == null
    }

    void "composite association filters reject incomplete rows and preserve complete tuples"() {
        given:
        def forwardEntity = runtimeEntityRegistry.getEntity(GapForwardChild)
        def reverseEntity = runtimeEntityRegistry.getEntity(GapReverseParent)
        def firstForward = Document.createDocument("tenant_key", "tenant-a")
            .put("reference_key", 1L)
        def secondForward = Document.createDocument("tenant_key", "tenant-b")
            .put("reference_key", 2L)
        def firstReverse = Document.createDocument("parent_tenant", "tenant-a")
            .put("parent_reference", 1L)
        def secondReverse = Document.createDocument("parent_tenant", "tenant-b")
            .put("parent_reference", 2L)

        when: "a forward lookup receives malformed, incomplete and two complete target rows"
        Filter forward = filterBuilder([
            "not a document",
            Document.createDocument("tenant_key", "incomplete"),
            firstForward,
            secondForward
        ]).buildFieldFilter(forwardEntity, "parent.name", ["\$eq": "match"], new Object[0], [:])

        then: "only correlated composite tuples match"
        matches(forward, Document.createDocument("parent_tenant", "tenant-a").put("parent_reference", 1L))
        matches(forward, Document.createDocument("parent_tenant", "tenant-b").put("parent_reference", 2L))
        !matches(forward, Document.createDocument("parent_tenant", "tenant-a").put("parent_reference", 2L))

        when: "one complete result selects the single-row return path"
        Filter singleForward = filterBuilder([firstForward])
            .buildFieldFilter(forwardEntity, "parent.name", ["\$eq": "match"], new Object[0], [:])

        then:
        matches(singleForward, Document.createDocument("parent_tenant", "tenant-a").put("parent_reference", 1L))

        when: "every forward result is malformed or incomplete"
        Filter emptyForward = filterBuilder([
            "not a document",
            Document.createDocument("tenant_key", "incomplete")
        ]).buildFieldFilter(forwardEntity, "parent.name", ["\$eq": "match"], new Object[0], [:])

        then:
        !matches(emptyForward, Document.createDocument("parent_tenant", "tenant-a").put("parent_reference", 1L))

        when: "an association has a composite target but declares no join columns"
        def unmappedEntity = runtimeEntityRegistry.getEntity(GapUnmappedCompositeChild)
        Filter unmapped = filterBuilder([firstForward])
            .buildFieldFilter(unmappedEntity, "parent.name", ["\$eq": "match"], new Object[0], [:])

        then:
        !matches(unmapped, Document.createDocument("id", "child"))

        when: "a reverse lookup receives malformed, incomplete and two complete child rows"
        Filter reverse = filterBuilder([
            99,
            Document.createDocument("parent_tenant", "incomplete"),
            firstReverse,
            secondReverse
        ]).buildFieldFilter(reverseEntity, "children.label", ["\$eq": "match"], new Object[0], [:])

        then: "the parent-side persisted fields are matched as correlated tuples"
        matches(reverse, Document.createDocument("tenant_key", "tenant-a").put("reference_key", 1L))
        matches(reverse, Document.createDocument("tenant_key", "tenant-b").put("reference_key", 2L))
        !matches(reverse, Document.createDocument("tenant_key", "tenant-a").put("reference_key", 2L))

        when: "one complete reverse result selects the single-row return path"
        Filter singleReverse = filterBuilder([firstReverse])
            .buildFieldFilter(reverseEntity, "children.label", ["\$eq": "match"], new Object[0], [:])

        then:
        matches(singleReverse, Document.createDocument("tenant_key", "tenant-a").put("reference_key", 1L))

        when: "every reverse result is malformed or incomplete"
        Filter emptyReverse = filterBuilder([
            99,
            Document.createDocument("parent_tenant", "incomplete")
        ]).buildFieldFilter(reverseEntity, "children.label", ["\$eq": "match"], new Object[0], [:])

        then:
        !matches(emptyReverse, Document.createDocument("tenant_key", "tenant-a").put("reference_key", 1L))
    }

    void "joined document sets hydrate a dedicated to-many association"() {
        given:
        def child = Document.createDocument("id", "child-1").put("label", "joined child")
        def parent = Document.createDocument("id", "parent-1")
            .put("tenant_key", "tenant-a")
            .put("reference_key", 1L)
            .put("display_name", "joined parent")
            .put("children", new LinkedHashSet<Document>([child]))

        when:
        GapReverseParent mapped = entityMapper.fromDocument(parent, GapReverseParent)

        then:
        mapped.children*.label == ["joined child"]

        when: "one referenced composite value is null during serialization"
        def incompleteParent = new GapForwardParent(tenantId: "tenant-a", referenceId: null, name: "partial")
        def incompleteChild = new GapForwardChild(id: "child-2", parent: incompleteParent)
        Document serialized = entityMapper.toDocument(incompleteChild)

        then: "the available join value is retained without writing a null component"
        serialized.get("parent_tenant") == "tenant-a"
        serialized.get("parent_reference") == null
    }

    private NitriteFilterBuilder filterBuilder(List<?> rows) {
        new NitriteFilterBuilder(entityMapper, {
            associatedEntity, filterMap, targetField, retainDocuments, params, namedParameters ->
                assert retainDocuments
                rows
        } as NitriteFilterBuilder.SubQueryExecutor)
    }

    private static boolean matches(Filter filter, Document document) {
        filter.apply(Pair.pair(NitriteId.newId(), document))
    }
}

@MappedEntity
class GapForwardParent {
    @Id
    @MappedProperty("tenant_key")
    String tenantId

    @Id
    @MappedProperty("reference_key")
    Long referenceId

    @MappedProperty("display_name")
    String name
}

@MappedEntity
class GapForwardChild {
    @Id
    String id

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns([
        @JoinColumn(name = "parent_tenant", referencedColumnName = "tenant_key"),
        @JoinColumn(name = "parent_reference", referencedColumnName = "reference_key")
    ])
    GapForwardParent parent
}

@MappedEntity
class GapUnmappedCompositeChild {
    @Id
    String id

    @Relation(Relation.Kind.MANY_TO_ONE)
    GapForwardParent parent
}

@MappedEntity
class GapReverseParent {
    @Id
    String id

    @MappedProperty("tenant_key")
    String tenantId

    @MappedProperty("reference_key")
    Long referenceId

    @MappedProperty("display_name")
    String name

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent")
    List<GapReverseChild> children = []
}

@MappedEntity
class GapReverseChild {
    @Id
    String id

    String label

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns([
        @JoinColumn(name = "parent_tenant", referencedColumnName = "tenant_key"),
        @JoinColumn(name = "parent_reference", referencedColumnName = "reference_key")
    ])
    GapReverseParent parent
}
