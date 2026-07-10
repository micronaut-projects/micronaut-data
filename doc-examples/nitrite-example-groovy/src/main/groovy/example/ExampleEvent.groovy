package example

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

// tag::event-type-mapping[]
@MappedEntity
class ExampleEvent {
    enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    @Id
    @GeneratedValue
    String id

    String type
    String payload
    Integer priority
    Status status
    LocalDate eventDate
    LocalDateTime eventDateTime
    Instant occurredAt
    BigDecimal amount
    Double score
    byte[] data
    List<String> tags
    Map<String, String> metadata
    Map<String, EventAttempt> attempts
    Optional<String> note
    EventLocation location

    ExampleEvent() {
    }

    ExampleEvent(String type, String payload, Status status, BigDecimal amount) {
        this.type = type
        this.payload = payload
        this.status = status
        this.amount = amount
    }

    @Embeddable
    static class EventLocation {
        String region
        String zone

        EventLocation() {
        }

        EventLocation(String region, String zone) {
            this.region = region
            this.zone = zone
        }
    }

    @Introspected
    @Serdeable
    static class EventAttempt {
        String handler
        Integer retries

        EventAttempt() {
        }

        EventAttempt(String handler, Integer retries) {
            this.handler = handler
            this.retries = retries
        }
    }
}
// end::event-type-mapping[]
