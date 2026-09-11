package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookRepositorySpec extends Specification {

    @Inject BookRepository bookRepository

    def cleanup() {
        bookRepository.deleteAll()
    }

    def "test CRUD operations"() {
        when: "a book is saved"
        Book book = new Book("The Stand")
        bookRepository.save(book)
        String id = book.getId()

        then: "it gets an ID"
        id != null

        when: "the book is read back"
        book = bookRepository.findById(id).orElse(null)

        then: "the title matches"
        book != null
        book.title == "The Stand"

        when: "the title is updated"
        bookRepository.update(id, "Changed")
        book = bookRepository.findById(id).orElse(null)

        then:
        book.title == "Changed"

        when: "the book is deleted"
        bookRepository.deleteById(id)

        then:
        bookRepository.count() == 0
    }

    def "find by title returns present optional"() {
        given:
        bookRepository.save(new Book("The Stand"))

        expect:
        bookRepository.findByTitle("The Stand").present
    }
}
