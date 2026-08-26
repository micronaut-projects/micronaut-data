package io.micronaut.data.nitrite.runtime

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder
import io.micronaut.data.nitrite.runtime.query.ast.NitriteFilterAST
import io.micronaut.data.nitrite.runtime.read.CollectionProjectionMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.collection.Document
import spock.lang.Specification

@MicronautTest(transactional = false)
class DefaultNitriteRepositoryOperationsSpec extends Specification {

    @Inject
    DefaultNitriteRepositoryOperations operations

    @Inject
    OperationsRepository repo

    @Inject
    ObjectMapper objectMapper

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry




    void "test malformed operators"() {
        given:
        def mapper = new NitriteEntityMapper(null, null, runtimeEntityRegistry)
        def builder = new NitriteFilterBuilder(mapper)

        when: "between is given a non-collection"
        builder.buildFieldFilter(null, "name", [("\$between"): "invalid"], null, null)

        then:
        def e1 = thrown(IllegalArgumentException)
        e1.message.contains("expected a two-element range")

        when: "between bounds are not comparable"
        builder.buildFieldFilter(null, "name", [("\$between"): [new Object(), new Object()]], null, null)

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message.contains("range bounds are not comparable")

        when: "not is given a non-map"
        builder.buildFieldFilter(null, "name", [("\$not"): "invalid"], null, null)

        then:
        def e3 = thrown(IllegalArgumentException)
        e3.message.contains("expected a nested operator object")

        when: "between is given null bounds"
        def noneFilter = builder.buildFieldFilter(null, "name", [("\$between"): [null, null]], null, null)

        then: "the range is unsatisfiable rather than unbounded, so nothing matches"
        !noneFilter.apply(null)
    }

    void "a toDouble expression coerces the stored value, or yields null when it cannot"() {
        given:
        def toDouble = new NitriteFilterAST.ExprValueNode.ToDouble(
            new NitriteFilterAST.ExprValueNode.FieldRef("someField"))

        expect:
        toDouble.evaluate(Document.createDocument("someField", stored), null, null) == expected

        where:
        stored   | expected
        "10.5"   | 10.5d
        10       | 10.0d
        "invalid"| null
    }

    void "test parseSortFromHints"() {
        expect:
        operations.parseSortFromHints(null) == null
        operations.parseSortFromHints([:]) == null
        operations.parseSortFromHints(["sort": "name:ASC"]).isSorted() == true
        operations.parseSortFromHints(["sort": "name:DESC"]).isSorted() == true
        operations.parseSortFromHints(["sort": "name:ASC,id:DESC"]).getOrderBy().size() == 2
    }

    void "test count with paged query"() {
        given:
        repo.save(new OperationsEntity(id: 3L, name: "C"))

        when:
        def pagedQuery = [
            getRootEntity: { -> OperationsEntity.class },
            getPageable: { -> Pageable.UNPAGED }
        ] as PagedQuery<OperationsEntity>
        def count = operations.count(pagedQuery)

        then:
        count == 1

        cleanup:
        repo.deleteAll()
    }

    void "test CollectionProjectionMapper"() {
        given:
        def valueConverter = new ValueConverter(ConversionService.SHARED)
        def entityMapper = new NitriteEntityMapper(
            ConversionService.SHARED,
            objectMapper,
            runtimeEntityRegistry
        )
        def mapper = new CollectionProjectionMapper(valueConverter, entityMapper)
        def doc = Document.createDocument("id", 1L).put("name", "Test")
        def persistentEntity = operations.getEntity(OperationsEntity)

        when: "single-field projection extracts and converts the value"
        def name = mapper.mapDocument(doc, ["name"], persistentEntity, String, false)

        then:
        name == "Test"

        when: "DTO projection maps via introspection"
        def dto = mapper.mapDocument(doc, ["id", "name"], persistentEntity, NameProjection, true)

        then:
        dto instanceof NameProjection
        dto.name == "Test"

        when: "multi-field native projection returns the raw document"
        def raw = mapper.mapDocument(doc, ["id", "name"], persistentEntity, Document, false)

        then:
        raw.is(doc)

        and: "null document yields null"
        mapper.mapDocument(null, ["name"], persistentEntity, String, false) == null
    }



}
