package io.micronaut.data.nitrite.tck

import io.micronaut.data.document.tck.entities.Document
import io.micronaut.data.model.Pageable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteDocumentRepositorySpec extends Specification {

    @Inject NitriteDocumentRepository repository

    def setup() {
        repository.deleteAll()
    }

    void "test save and find by id"() {
        given:
        Document doc = new Document()
        doc.title = "Test Doc"
        repository.save(doc)

        when:
        def found = repository.findById(doc.id).orElse(null)

        then:
        found != null
        found.title == "Test Doc"
    }

    void "test save and find by title"() {
        given:
        repository.save(new Document(title: "Title 1"))
        repository.save(new Document(title: "Title 2"))

        expect:
        repository.findByTitle("Title 1").isPresent()
        repository.findByTitle("Title 2").isPresent()
        !repository.findByTitle("Title 3").isPresent()
    }

    void "test update"() {
        given:
        Document doc = new Document(title: "Old Title")
        repository.save(doc)

        when:
        repository.updateTitle(doc.id, "New Title")
        def found = repository.findById(doc.id).get()

        then:
        found.title == "New Title"
    }

    void "test delete"() {
        given:
        Document doc = new Document(title: "To Delete")
        repository.save(doc)

        when:
        repository.deleteById(doc.id)

        then:
        !repository.findById(doc.id).isPresent()
    }

    void "test pagination"() {
        given:
        (1..10).each { i ->
            repository.save(new Document(title: "Doc $i"))
        }

        when:
        def page = repository.findAll(Pageable.from(0, 5))

        then:
        page.content.size() == 5
        repository.count() == 10
    }
}
