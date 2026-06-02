package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class VersionedBookRepositorySpec extends Specification {

    @Inject VersionedBookRepository versionedBookRepository

    def cleanup() {
        versionedBookRepository.deleteAll()
    }

    def "version is initialized to 0 on first save"() {
        given:
        VersionedBook book = new VersionedBook("Initial Title")

        when:
        versionedBookRepository.save(book)

        then:
        book.id != null
        book.version == 0
        versionedBookRepository.findById(book.id).get().version == 0
    }

    def "delete with correct version succeeds"() {
        given:
        VersionedBook book = new VersionedBook("To Delete")
        versionedBookRepository.save(book)

        when:
        versionedBookRepository.delete(book.id, book.version)

        then:
        versionedBookRepository.findById(book.id).isEmpty()
    }
}
