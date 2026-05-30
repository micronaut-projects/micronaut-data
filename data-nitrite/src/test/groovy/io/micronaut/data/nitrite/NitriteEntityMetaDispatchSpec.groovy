package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Book
import io.micronaut.data.nitrite.model.CustomTag
import io.micronaut.data.nitrite.model.MappingTestEntity
import io.micronaut.data.nitrite.repository.BookRepository
import io.micronaut.data.nitrite.repository.MappingTestRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import spock.lang.Specification

/**
 * Explicit coverage for the NitriteEntityMeta dispatch switch.
 * The old convertToDocumentInternal() was tested implicitly through repository specs;
 * these tests target the edge-case strategies that are otherwise hard to observe.
 */
@MicronautTest(transactional = false)
class NitriteEntityMetaDispatchSpec extends Specification {

    @Inject
    MappingTestRepository repository

    @Inject
    BookRepository bookRepository

    @Inject
    Nitrite nitrite

    def setup() {
        repository.deleteAll()
        bookRepository.deleteAll()
    }

    void "@Transient field is excluded from the document and null on reload"() {
        given:
        def entity = new MappingTestEntity()
        entity.name = "transient-test"
        entity.transientField = "should not be persisted"

        when:
        def saved = repository.save(entity)
        def reloaded = repository.findById(saved.id).orElse(null)

        then: "the entity is found"
        reloaded != null
        reloaded.name == "transient-test"

        and: "the @Transient field is absent from the document — null on reload"
        reloaded.transientField == null

        and: "the Nitrite document does not contain the transient field key"
        def docs = nitrite.getCollection("mapping_test_entity").find().toList()
        docs.size() == 1
        docs[0].get("transientField") == null
    }

    void "INTROSPECTED_POJO strategy stores a non-Serializable @Introspected POJO as a nested map and round-trips it"() {
        // CustomTag is @Introspected but does NOT implement Serializable.
        // The mapper must use BeanIntrospection (INTROSPECTED_POJO strategy), not Serde or
        // Java serialization. The Nitrite document must contain a plain Map, not a blob.
        given:
        def entity = new MappingTestEntity()
        entity.name = "introspected-pojo-test"
        entity.tag = new CustomTag("env", "production")

        when:
        def saved = repository.save(entity)
        def reloaded = repository.findById(saved.id).orElse(null)

        then: "the CustomTag fields round-trip correctly"
        reloaded != null
        reloaded.tag != null
        reloaded.tag.key == "env"
        reloaded.tag.value == "production"

        and: "the Nitrite document stores the tag as a nested map — no Serializable blob"
        def docs = nitrite.getCollection("mapping_test_entity").find().toList()
        docs.size() == 1
        def storedTag = docs[0].get("tag")
        storedTag instanceof Map
        (storedTag as Map).get("key") == "env"
        (storedTag as Map).get("value") == "production"
    }

    void "non-@Relation entity property is stored as a foreign-key reference by the annotation processor"() {
        // The Micronaut Data annotation processor auto-classifies Book-typed properties as
        // RuntimeAssociation even without an explicit @Relation annotation. Without
        // mappedBy or explicit embed config it defaults to ASSOCIATION_ID_REF, storing only
        // the associated entity's ID under the join-column name (bookRef -> book_ref_id).
        given:
        def book = bookRepository.save(new Book("Domain-Driven Design"))

        def entity = new MappingTestEntity()
        entity.name = "association-embedded-test"
        entity.bookRef = book

        when:
        repository.save(entity)

        then: "the document contains the Book's ID under the join-column name book_ref_id"
        def docs = nitrite.getCollection("mapping_test_entity").find().toList()
        docs.size() == 1
        def storedBookRefId = docs[0].get("book_ref_id")
        storedBookRefId != null
        storedBookRefId == book.id
    }

    void "mappedByAssocs is empty for entities with no bi-directional associations"() {
        given: "MappingTestEntity has no @Relation(mappedBy=...) associations"
        def entity = new MappingTestEntity()
        entity.name = "no-assoc"

        when: "persistOne() is called internally by save()"
        def saved = repository.save(entity)

        then: "no NPE — the empty mappedByAssocs list was safely iterated"
        saved.id != null
    }
}
