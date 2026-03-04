package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.data.nitrite.service.EventService
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Tests @Transactional behaviour through a service.
 *
 * Opts back in to transactional = true so each test runs inside a transaction
 * that is rolled back on completion, giving test isolation.
 * Individual tests exercise commit and rollback via the EventService.
 */
@MicronautTest(transactional = true)
class NitriteTransactionSpec extends Specification {

    @Inject EventRepository repo
    @Inject EventService svc

    void "transactional commit persists the entity"() {
        when:
        def saved = svc.saveEvent(new Event("TX_COMMIT", "ok"))

        then:
        repo.findById(saved.id).isPresent()
    }

    void "transactional rollback on exception removes the entity"() {
        when:
        svc.saveAndFail(new Event("TX_ROLLBACK", "fail"))

        then:
        thrown(RuntimeException)
        repo.findByType("TX_ROLLBACK").isEmpty()
    }
}
