package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class TimestampedRecordRepositorySpec extends Specification {

    @Inject TimestampedRecordRepository repository

    def cleanup() {
        repository.deleteAll()
    }

    // tag::pre-persist-listener-usage[]
    def "pre-persist listener can veto"() {
        given:
        repository.saveAll([
            new TimestampedRecord("keep"),
            new TimestampedRecord("veto-me")
        ])

        expect:
        def records = repository.findAll()
        records.size() == 1
        records[0].name == "keep"
    }
    // end::pre-persist-listener-usage[]
}
