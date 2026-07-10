package example

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

// tag::event-type-mapping[]
@MappedEntity
class ExampleEvent {
    enum class Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    @Id
    @GeneratedValue
    var id: String? = null

    var type: String? = null
    var payload: String? = null
    var priority: Int? = null
    var status: Status? = null
    var eventDate: LocalDate? = null
    var eventDateTime: LocalDateTime? = null
    var occurredAt: Instant? = null
    var amount: BigDecimal? = null
    var score: Double? = null
    var data: ByteArray? = null
    var tags: List<String>? = null
    var metadata: Map<String, String>? = null
    var attempts: Map<String, EventAttempt>? = null
    var note: Optional<String>? = null
    var location: EventLocation? = null

    constructor()

    constructor(type: String, payload: String?, status: Status, amount: BigDecimal) {
        this.type = type
        this.payload = payload
        this.status = status
        this.amount = amount
    }

    @Embeddable
    class EventLocation {
        var region: String? = null
        var zone: String? = null

        constructor()

        constructor(region: String, zone: String) {
            this.region = region
            this.zone = zone
        }
    }

    @Introspected
    @Serdeable
    class EventAttempt {
        var handler: String? = null
        var retries: Int? = null

        constructor()

        constructor(handler: String, retries: Int) {
            this.handler = handler
            this.retries = retries
        }
    }
}
// end::event-type-mapping[]
