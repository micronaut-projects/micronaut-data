package io.micronaut.data.search.elasticsearch.memory

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
@Property(name = "spec.name", value = "DocumentSearchOperationsSpec")
@Property(name = "micronaut.data.search.max-results", value = "50")
class MaxResultsSpec extends Specification {

    @Inject
    InMemorySearchTransport transport

    @Inject
    NoteRepository notes

    void "an unpaged query fetches at most max-results hits"() {
        when:
        notes.findByPagesGreaterThanEqualsAndPagesLessThanEquals(1, 10)

        then:
        transport.lastBody().contains('"size":50')
    }
}
