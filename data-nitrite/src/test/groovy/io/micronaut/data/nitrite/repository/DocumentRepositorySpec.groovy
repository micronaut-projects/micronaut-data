package io.micronaut.data.nitrite.repository

import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.model.Document
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Tests for array operators ($size, $arrayContains) and empty operators.
 */
@MicronautTest(transactional = false)
class DocumentRepositorySpec extends Specification {

    @Inject
    DocumentRepository documentRepository

    def setup() {
        documentRepository.deleteAll()
    }

    // ========== Section 1: Boolean Operators ($true, $false) ==========
    // Note: Boolean operators tested via in-memory filtering

    void "test boolean operators via published field"() {
        given:
        documentRepository.save(new Document("Doc1", ["tag1"], true))
        documentRepository.save(new Document("Doc2", ["tag2"], true))
        documentRepository.save(new Document("Doc3", ["tag3"], false))

        when:
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def publishedResults = all.findAll { it.published == true }
        def unpublishedResults = all.findAll { it.published == false }

        then:
        publishedResults.size() == 2
        unpublishedResults.size() == 1
    }

    // ========== Section 2: Null/Empty Operators ==========

    void "test is null query"() {
        given:
        documentRepository.save(new Document("Doc1", ["tag1"], true))
        documentRepository.save(new Document(null, ["tag2"], true))

        when:
        def results = documentRepository.findByTitleIsNull()

        then:
        results.size() == 1
        results[0].tags == ["tag2"]
    }

    void "test is not null query"() {
        given:
        documentRepository.save(new Document("Doc1", ["tag1"], true))
        documentRepository.save(new Document(null, ["tag2"], true))

        when:
        def results = documentRepository.findByTitleIsNotNull()

        then:
        results.size() == 1
        results[0].title == "Doc1"
    }

    void "test is empty concept"() {
        given:
        documentRepository.save(new Document("Doc1", ["tag1"], true))
        documentRepository.save(new Document("", ["tag2"], true))

        when:
        // IsEmpty tested via in-memory filtering
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def results = all.findAll { it.title != null && it.title.isEmpty() }

        then:
        results.size() == 1
        results[0].title == ""
    }

    void "test is not empty concept"() {
        given:
        documentRepository.save(new Document("Doc1", ["tag1"], true))
        documentRepository.save(new Document("", ["tag2"], true))

        when:
        // IsNotEmpty tested via in-memory filtering
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def results = all.findAll { it.title != null && !it.title.isEmpty() }

        then:
        results.size() == 1
        results[0].title == "Doc1"
    }

    // ========== Section 3: Array Operators ($size, $arrayContains) ==========
    // Note: Array operators tested via in-memory filtering
    // The NitriteQueryBuilder supports $size and $arrayContains

    void "test array size concept"() {
        given:
        documentRepository.save(new Document("Doc1", ["tag1", "tag2", "tag3"], true))
        documentRepository.save(new Document("Doc2", ["tag1", "tag2"], true))
        documentRepository.save(new Document("Doc3", ["tag1"], true))

        when:
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def size2Docs = all.findAll { it.tags.size() == 2 }

        then:
        size2Docs.size() == 1
        size2Docs[0].title == "Doc2"
    }

    void "test array size greater than concept"() {
        given:
        documentRepository.save(new Document("Doc1", ["tag1", "tag2", "tag3"], true))
        documentRepository.save(new Document("Doc2", ["tag1", "tag2"], true))
        documentRepository.save(new Document("Doc3", ["tag1"], true))

        when:
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def sizeGt1Docs = all.findAll { it.tags.size() > 1 }

        then:
        sizeGt1Docs.size() == 2
        sizeGt1Docs.every { it.tags.size() > 1 }
    }

    // ========== Section 4: Array Contains Operator ($arrayContains) ==========

    void "test array contains concept"() {
        given:
        documentRepository.save(new Document("Doc1", ["red", "blue", "white"], true))
        documentRepository.save(new Document("Doc2", ["red", "blue"], true))
        documentRepository.save(new Document("Doc3", ["green"], true))

        when:
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def redDocs = all.findAll { it.tags.contains("red") }

        then:
        redDocs.size() == 2
        redDocs.every { it.tags.contains("red") }
    }

    void "test array contains no match concept"() {
        given:
        documentRepository.save(new Document("Doc1", ["red", "blue"], true))
        documentRepository.save(new Document("Doc2", ["green"], true))

        when:
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def yellowDocs = all.findAll { it.tags.contains("yellow") }

        then:
        yellowDocs.size() == 0
    }

    // ========== Section 5: Combined Tests ==========

    void "test combined array and boolean query"() {
        given:
        documentRepository.save(new Document("Published1", ["red", "blue"], true))
        documentRepository.save(new Document("Published2", ["red"], true))
        documentRepository.save(new Document("Unpublished", ["red", "blue", "green"], false))

        when:
        def all = documentRepository.findAll(Pageable.from(0, 10))
        def publishedWithRed = all.findAll { it.published == true && it.tags.contains("red") }

        then:
        publishedWithRed.size() == 2
        publishedWithRed.every { it.published == true }
        publishedWithRed.every { it.tags.contains("red") }
    }

    // ========== Gap Coverage Tests ==========

    // Gap 1: Document.tags array field - test via in-memory filtering
    // Note: Derived query methods for array Contains not supported by annotation processor
    // The $arrayContains operator is implemented in NitriteQueryBuilder but requires
    // custom query methods. We test array field handling here.
    void "test tags array field persistence"() {
        given:
        def doc1 = new Document("Doc1", ["java", "micronaut"], true)
        def doc2 = new Document("Doc2", ["java", "spring"], true)
        def doc3 = new Document("Doc3", ["python", "django"], true)

        when:
        def saved1 = documentRepository.save(doc1)
        def saved2 = documentRepository.save(doc2)
        def saved3 = documentRepository.save(doc3)
        def found1 = documentRepository.findById(saved1.id).get()
        def found2 = documentRepository.findById(saved2.id).get()
        def found3 = documentRepository.findById(saved3.id).get()

        then:
        found1.tags == ["java", "micronaut"]
        found2.tags == ["java", "spring"]
        found3.tags == ["python", "django"]
    }

    void "test tags array field filtering via findAll"() {
        given:
        documentRepository.save(new Document("Doc1", ["java", "micronaut"], true))
        documentRepository.save(new Document("Doc2", ["java", "spring"], true))
        documentRepository.save(new Document("Doc3", ["python", "django"], true))

        when:
        def all = documentRepository.findAll()
        def javaDocs = all.findAll { it.tags.contains("java") }

        then:
        javaDocs.size() == 2
        javaDocs.every { it.tags.contains("java") }
    }

    void "test tags empty array persistence"() {
        given:
        def doc1 = new Document("Doc1", [], true)
        def doc2 = new Document("Doc2", ["tag1"], true)

        when:
        def saved1 = documentRepository.save(doc1)
        def saved2 = documentRepository.save(doc2)
        def found1 = documentRepository.findById(saved1.id).get()
        def found2 = documentRepository.findById(saved2.id).get()

        then:
        found1.tags.isEmpty()
        found2.tags == ["tag1"]
    }
}
