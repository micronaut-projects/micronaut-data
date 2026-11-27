package io.micronaut.data.search.elasticsearch.memory

import io.micronaut.context.annotation.Property
import io.micronaut.data.exceptions.NonUniqueResultException
import io.micronaut.data.model.Sort
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * With unique results enabled, a single-result query fetches a second hit to detect several matches,
 * while a findFirst keeps its own limit of one.
 */
@MicronautTest(transactional = false)
@Property(name = "spec.name", value = "DocumentSearchOperationsSpec")
@Property(name = "micronaut.data.search.unique-result-on-find-one", value = "true")
class UniqueFindOneSpec extends Specification {

    @Inject
    InMemorySearchTransport transport

    @Inject
    NoteRepository notes

    void setup() {
        transport.clear()
    }

    void "a single-result query fails when a second document matches"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Coffee", "Grinding beans", 80)])

        when:
        notes.getByTitle("Coffee")

        then:
        NonUniqueResultException e = thrown()
        e.message.startsWith("Multiple results found for query")
        transport.lastBody().contains('"size":2')
    }

    void "a findFirst keeps its own limit of one"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 80)])

        when:
        Note first = notes.findFirstByPagesGreaterThan(1, Sort.of(Sort.Order.desc("title")))

        then:
        first != null
        transport.lastBody().contains('"size":1')
    }

    void "the configuration rejects a non-positive hit ceiling and holds the unique-result setting"() {
        given:
        DocumentSearchConfiguration configuration = new DocumentSearchConfiguration()

        when:
        configuration.setMaxResults(0)

        then:
        thrown(IllegalArgumentException)

        when:
        configuration.setUniqueResultOnFindOne(true)

        then:
        configuration.isUniqueResultOnFindOne()
        configuration.getMaxResults() == DocumentSearchConfiguration.DEFAULT_MAX_RESULTS
    }
}
