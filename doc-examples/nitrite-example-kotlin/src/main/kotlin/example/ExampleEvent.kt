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

@MappedEntity
class ExampleEvent {

    @Id
    @GeneratedValue
    var id: String? = null

    var type: String? = null
    var payload: String? = null
    var priority: Int? = null
    var score: Double? = null

    // tag::event-type-mapping[]
    enum class Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    var status: Status? = null // <1>
    var eventDate: LocalDate? = null // <2>
    var eventDateTime: LocalDateTime? = null // <2>
    var occurredAt: Instant? = null // <2>
    var amount: BigDecimal? = null // <3>
    var data: ByteArray? = null // <4>
    var tags: List<String>? = null // <5>
    var metadata: Map<String, String>? = null // <6>
    var attempts: Map<String, EventAttempt>? = null // <6>
    var note: Optional<String>? = null // <7>
    var location: EventLocation? = null // <8>
    // end::event-type-mapping[]

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
