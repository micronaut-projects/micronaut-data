package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.IntegerIdEntity
import io.micronaut.data.nitrite.model.LongIdEntity
import io.micronaut.data.nitrite.model.StringIdEntity
import io.micronaut.data.nitrite.model.Widget
import io.micronaut.data.nitrite.repository.IntegerIdEntityRepository
import io.micronaut.data.nitrite.repository.LongIdRepository
import io.micronaut.data.nitrite.repository.StringIdRepository
import io.micronaut.data.nitrite.repository.WidgetRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import spock.lang.Specification
import spock.lang.Unroll

import java.util.UUID
import org.dizitart.no2.collection.Document
import org.dizitart.no2.exceptions.UniqueConstraintException

@MicronautTest(transactional = false)
class NitriteGeneratedIdSpec extends Specification {

    @Inject
    WidgetRepository widgetRepository

    @Inject
    IntegerIdEntityRepository integerIdEntityRepository

    @Inject
    StringIdRepository stringIdRepository

    @Inject
    LongIdRepository longIdRepository

    @Inject
    Nitrite nitrite

    def cleanup() {
        widgetRepository.deleteAll()
        integerIdEntityRepository.deleteAll()
        stringIdRepository.deleteAll()
        longIdRepository.deleteAll()
    }

    def "a derived identity query on a Long identity is answered without an index on the identity field"() {
        given: "a Long identity, which is written as the document key and so carries no index"
            def saved = longIdRepository.save(new LongIdEntity(null, "keyed"))

        expect: "the identity field is unindexed, so only the document key can answer a seek"
            !nitrite.getCollection("LongIdEntity").hasIndex("id")

        and: "and the derived query, which reaches its predicate by field name, still finds it"
            longIdRepository.queryById(saved.id).get().name == "keyed"

        and: "an identity that was never stored finds nothing"
            longIdRepository.queryById(saved.id + 1).isEmpty()
    }

    def "scalar identity CRUD works and is automatically indexed for #idType.simpleName"() {
        given:
            def repository = repositoryFor(idType)
            def entity = entityFor(idType)

        when:
            def saved = repository.save(entity)

        then:
            saved.id != null
            saved.id.class == idType
            if (usesDocumentKey) {
                assert nitrite.getCollection(collectionName).find().first().getId().getIdValue() == saved.id
            } else {
                assert nitrite.getCollection(collectionName).hasIndex("id")
            }

        when:
            def found = repository.findById(saved.id).orElse(null)

        then:
            found != null
            found.name == "Test ${idType.simpleName} Entity"

        when:
            def replacement = entityFor(idType)
            replacement.id = saved.id
            replacement.name = "Upserted ${idType.simpleName} Entity"
            def upserted = repository.save(replacement)

        then:
            repository.count() == 1
            upserted.id == saved.id
            repository.findById(saved.id).orElse(null).name == "Upserted ${idType.simpleName} Entity"

        when:
            upserted.name = "Updated ${idType.simpleName} Entity"
            repository.update(upserted)
            found = repository.findById(saved.id).orElse(null)

        then:
            found != null
            found.name == "Updated ${idType.simpleName} Entity"

        when:
            repository.deleteById(saved.id)

        then:
            repository.findById(saved.id).isEmpty()

        where:
            idType  | collectionName    | usesDocumentKey
            String  | "StringIdEntity"  | false
            Integer | "IntegerIdEntity" | false
            Long    | "LongIdEntity"    | true
            UUID    | "widgets"         | false
    }

    def "Long identity does not get a redundant secondary index"() {
        given: "Long IDs are stored in Nitrite's _id document key, so a secondary index is redundant"
            def entity = new LongIdEntity(name: "No secondary index")

        when:
            longIdRepository.save(entity)

        then: "the collection has no secondary index on the id field"
            !nitrite.getCollection("LongIdEntity").hasIndex("id")
    }

    private Object repositoryFor(Class idType) {
        if (idType == String) {
            return stringIdRepository
        }
        if (idType == Integer) {
            return integerIdEntityRepository
        }
        if (idType == Long) {
            return longIdRepository
        }
        if (idType == UUID) {
            return widgetRepository
        }
        throw new IllegalArgumentException("Unsupported identity type: ${idType}")
    }

    private Object entityFor(Class idType) {
        if (idType == String) {
            return new StringIdEntity(name: "Test String Entity")
        }
        if (idType == Integer) {
            return new IntegerIdEntity(name: "Test Integer Entity")
        }
        if (idType == Long) {
            return new LongIdEntity(name: "Test Long Entity")
        }
        if (idType == UUID) {
            return new Widget(name: "Test UUID Entity")
        }
        throw new IllegalArgumentException("Unsupported identity type: ${idType}")
    }

    @Unroll
    def "database rejects duplicate identities on insert for #idType.simpleName"() {
        given:
        def collection = nitrite.getCollection(collectionName)
        def id = generateIdFor(idType)
        // Long identity is stored in Nitrite's _id document key by the entity mapper;
        // other types use a UNIQUE index on the "id" field.
        def doc1 = Document.createDocument("id", id).put("name", "First")
        def doc2 = Document.createDocument("id", id).put("name", "Second")
        if (idType == Long) {
            doc1.put("_id", id)
            doc2.put("_id", id)
        }

        when:
        collection.insert(doc1)
        collection.insert(doc2)

        then:
        thrown(UniqueConstraintException)

        where:
        idType  | collectionName
        String  | "StringIdEntity"
        Integer | "IntegerIdEntity"
        Long    | "LongIdEntity"
        UUID    | "widgets"
    }

    private Object generateIdFor(Class idType) {
        if (idType == String) return UUID.randomUUID().toString()
        if (idType == Integer) return 123456
        if (idType == Long) return 123456789L
        if (idType == UUID) return UUID.randomUUID()
        throw new IllegalArgumentException("Unsupported identity type: ${idType}")
    }
}
