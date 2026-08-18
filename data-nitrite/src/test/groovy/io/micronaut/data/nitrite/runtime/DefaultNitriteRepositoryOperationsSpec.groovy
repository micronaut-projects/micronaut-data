package io.micronaut.data.nitrite.runtime

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.model.DataType
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.model.runtime.PreparedQuery
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder
import io.micronaut.data.nitrite.runtime.query.ast.NitriteFilterAST
import io.micronaut.data.nitrite.runtime.read.CollectionProjectionMapper
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.collection.Document
import spock.lang.Specification
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

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

    void "test resolve escape char in criteria API"() {
        given:
        def e1 = new OperationsEntity(name: "A%B")
        def e2 = new OperationsEntity(name: "AxB")
        repo.saveAll([e1, e2])

        when:
        def spec = PredicateSpecification.where({ root, cb ->
            cb.like(root.get("name"), cb.literal("A\\%B"), cb.literal('\\' as char))
        })
        def list = repo.findAll(spec)

        then:
        list.size() == 1
        list[0].name == "A%B"

        cleanup:
        repo.deleteAll()
    }

    void "test char filtering"() {
        given:
        def e1 = new OperationsEntity(name: "exec1")
        e1.setInitial('A' as char)
        repo.save(e1)

        when:
        def list = repo.findByInitial('A' as char)

        then:
        list.size() == 1
        list[0].initial == ('A' as char)

        cleanup:
        repo.deleteAll()
    }

    void "test DTO projection findOne and findAll"() {
        given:
        repo.save(new OperationsEntity(id: 101L, name: "dtoTest"))

        when:
        def singleDto = repo.getByName("dtoTest")
        def listDto = repo.queryByName("dtoTest")

        then:
        singleDto != null
        singleDto.name == "dtoTest"
        listDto.size() == 1
        listDto[0].name == "dtoTest"

        cleanup:
        repo.deleteAll()
    }

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

    void "a cursored page reads in sort order"() {
        given:
        repo.saveAll([
            new OperationsEntity(id: 1L, name: "A"),
            new OperationsEntity(id: 2L, name: "B"),
            new OperationsEntity(id: 3L, name: "C")
        ])

        when:
        def page = repo.findAll(Pageable.afterCursor(null, 0, 2, Sort.of(Sort.Order.asc("name"))))

        then:
        page.content*.name == ["A", "B"]

        cleanup:
        repo.deleteAll()
    }

    void "updateAll writes every entity it is given"() {
        given:
        repo.saveAll([
            new OperationsEntity(id: 1L, name: "A"),
            new OperationsEntity(id: 2L, name: "B"),
            new OperationsEntity(id: 3L, name: "C")
        ])

        when:
        def entities = repo.findAll().toList()
        entities.each { it.name = it.name + " updated" }
        repo.updateAll(entities)

        then:
        repo.findAll()*.name.toSorted() == ["A updated", "B updated", "C updated"]

        cleanup:
        repo.deleteAll()
    }

    void "deleteAll removes only the entities it is given"() {
        given:
        repo.saveAll([
            new OperationsEntity(id: 1L, name: "A"),
            new OperationsEntity(id: 2L, name: "B"),
            new OperationsEntity(id: 3L, name: "C")
        ])

        when:
        repo.deleteAll(repo.findAll().toList().findAll { it.name != "C" })

        then:
        repo.findAll()*.name == ["C"]

        cleanup:
        repo.deleteAll()
    }
}
