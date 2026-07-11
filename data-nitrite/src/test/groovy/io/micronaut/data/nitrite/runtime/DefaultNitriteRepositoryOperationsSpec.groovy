package io.micronaut.data.nitrite.runtime

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.data.model.DataType
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.runtime.PreparedQuery
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MicronautTest(transactional = false)
class DefaultNitriteRepositoryOperationsSpec extends Specification {

    @Inject
    DefaultNitriteRepositoryOperations operations

    @Inject
    MyTestRepo repo

    @Inject
    io.micronaut.serde.ObjectMapper objectMapper

    @Inject
    io.micronaut.data.model.runtime.RuntimeEntityRegistry runtimeEntityRegistry

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
        repo.save(new MyTestEntity(id: 3L, name: "C"))
        
        when:
        def pagedQuery = [
            getRootEntity: { -> MyTestEntity.class },
            getPageable: { -> Pageable.UNPAGED }
        ] as io.micronaut.data.model.runtime.PagedQuery<MyTestEntity>
        def count = operations.count(pagedQuery)
        
        then:
        count == 1
        
        cleanup:
        repo.deleteAll()
    }

    void "test CollectionProjectionMapper"() {
        given:
        def valueConverter = new ValueConverter(io.micronaut.core.convert.ConversionService.SHARED)
        def entityMapper = new io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper(
            io.micronaut.core.convert.ConversionService.SHARED,
            objectMapper,
            runtimeEntityRegistry
        )
        def mapper = new io.micronaut.data.nitrite.runtime.read.CollectionProjectionMapper(valueConverter, entityMapper)
        def doc = org.dizitart.no2.collection.Document.createDocument("id", 1L).put("name", "Test")
        def persistentEntity = operations.getEntity(MyTestEntity)
        
        when: "single-field projection extracts and converts the value"
        def name = mapper.mapDocument(doc, ["name"], persistentEntity, String, false)

        then:
        name == "Test"

        when: "DTO projection maps via introspection"
        def dto = mapper.mapDocument(doc, ["id", "name"], persistentEntity, MyTestDTO, true)

        then:
        dto instanceof MyTestDTO
        dto.name == "Test"

        when: "multi-field native projection returns the raw document"
        def raw = mapper.mapDocument(doc, ["id", "name"], persistentEntity, org.dizitart.no2.collection.Document, false)

        then:
        raw.is(doc)

        and: "null document yields null"
        mapper.mapDocument(null, ["name"], persistentEntity, String, false) == null
    }

}

@io.micronaut.core.annotation.Introspected
class MyTestDTO {
    String name
}
