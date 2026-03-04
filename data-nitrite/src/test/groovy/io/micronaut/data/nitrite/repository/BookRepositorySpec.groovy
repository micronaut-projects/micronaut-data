package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookRepositorySpec extends Specification {

    @Inject
    BookRepository repository

    def setup() {
        repository.deleteAll()
    }

    void "crud + explicit update method works"() {
        when:
        def saved = repository.save(new Book("The Stand"))

        then:
        saved.id
        repository.findById(saved.id).get().title == "The Stand"
        repository.findByTitle("The Stand").isPresent()

        when:
        repository.update(saved.id, "Changed")

        then:
        repository.findById(saved.id).get().title == "Changed"

        when:
        repository.deleteById(saved.id)

        then:
        !repository.findById(saved.id).isPresent()
    }
}

