package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class VersionedBookTemporalRepositorySpec extends Specification {

    @Inject VersionedBookTemporalRepository versionedBookTemporalRepository

    def cleanup() {
        versionedBookTemporalRepository.deleteAll()
    }

    def "temporal version is set on first save"() {
        given:
        VersionedBookTemporal book = new VersionedBookTemporal("Initial Title")

        when:
        versionedBookTemporalRepository.save(book)

        then:
        book.id != null
        book.version != null
        versionedBookTemporalRepository.findById(book.id).get().version != null
    }

    def "delete with correct temporal version succeeds"() {
        given:
        VersionedBookTemporal book = new VersionedBookTemporal("To Delete")
        versionedBookTemporalRepository.save(book)

        when:
        versionedBookTemporalRepository.delete(book.id, book.version)

        then:
        versionedBookTemporalRepository.findById(book.id).isEmpty()
    }
}
