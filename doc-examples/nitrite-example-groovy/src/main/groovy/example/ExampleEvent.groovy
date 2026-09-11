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

@MappedEntity
class ExampleEvent {

    @Id
    @GeneratedValue
    String id

    String type
    String payload
    Integer priority
    Double score

    // tag::event-type-mapping[]
    enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    Status status // <1>
    LocalDate eventDate // <2>
    LocalDateTime eventDateTime // <2>
    Instant occurredAt // <2>
    BigDecimal amount // <3>
    byte[] data // <4>
    List<String> tags // <5>
    Map<String, String> metadata // <6>
    Map<String, EventAttempt> attempts // <6>
    Optional<String> note // <7>
    EventLocation location // <8>
    // end::event-type-mapping[]

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
