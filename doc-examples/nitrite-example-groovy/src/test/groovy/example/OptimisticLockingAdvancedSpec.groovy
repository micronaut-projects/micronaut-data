package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class OptimisticLockingAdvancedSpec extends Specification {

    @Inject VersionedBookRepository versionedBookRepository
    @Inject VersionedBookTemporalRepository versionedBookTemporalRepository

    def cleanup() {
        versionedBookRepository.deleteAll()
        versionedBookTemporalRepository.deleteAll()
    }

    def "version is initialized to 0 on save"() {
        given:
        VersionedBook book = new VersionedBook("Initial Title")

        when:
        versionedBookRepository.save(book)

        then:
        book.version == 0
        versionedBookRepository.findById(book.id).get().version == 0
    }

    def "temporal version is set on save"() {
        given:
        VersionedBookTemporal book = new VersionedBookTemporal("Initial")

        when:
        versionedBookTemporalRepository.save(book)

        then:
        book.version != null
        versionedBookTemporalRepository.findById(book.id).get().version != null
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

    def "delete temporal with correct version succeeds"() {
        given:
        VersionedBookTemporal book = new VersionedBookTemporal("To Delete")
        versionedBookTemporalRepository.save(book)

        when:
        versionedBookTemporalRepository.delete(book.id, book.version)

        then:
        versionedBookTemporalRepository.findById(book.id).isEmpty()
    }
}
