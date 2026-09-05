package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Phase 4: Type-Diverse Mapping round-trip test via Event entity extension.
 * Exercises NitriteEntityMapper gaps: serializeForDocument (0%),
 * toDocumentValue (40%), fromDocumentInternal (78%), convertToDocumentInternal (81%).
 *
 * Covers: enum Status, LocalDate, LocalDateTime, Instant, BigDecimal, byte[], nested @Embeddable,
 * List<String>, Map<String, String>, Optional<String>.
 */
@MicronautTest(transactional = false)
class TypeDiverseEventSpec extends Specification {

    @Inject
    EventRepository repository

    def setup() {
        repository.deleteAll()
    }

    void "event with diverse field types round-trip: save and find preserves all types"() {
        given:
        def now = Instant.now()
        def date = LocalDate.of(2024, 1, 15)
        def dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45)
        def amount = new BigDecimal("123.45")
        def data = "test data".bytes
        def tags = ["tag1", "tag2", "tag3"]
        def metadata = ["key1": "value1", "key2": "value2"]
        def location = new Event.EventLocation("us-west", "zone-a")

        def event = new Event(
            "diverse-test",
            "test payload",
            Event.Status.ACTIVE,
            date,
            dateTime,
            now,
            amount,
            data,
            tags,
            metadata,
            Optional.empty(),
            location
        )

        when:
        def saved = repository.save(event)

        then: "event is saved with generated id"
        saved.id != null

        when:
        def reloaded = repository.findById(saved.id).orElse(null)

        then: "all fields round-trip correctly"
        reloaded != null
        reloaded.type == "diverse-test"
        reloaded.payload == "test payload"
        reloaded.status == Event.Status.ACTIVE
        reloaded.dateCreated == date
        reloaded.dateTimeCreated == dateTime
        reloaded.occurredAt == now
        reloaded.amount == amount
        reloaded.data == data
        reloaded.tags == tags
        reloaded.metadata == metadata
        reloaded.location.region == "us-west"
        reloaded.location.zone == "zone-a"
    }

}
