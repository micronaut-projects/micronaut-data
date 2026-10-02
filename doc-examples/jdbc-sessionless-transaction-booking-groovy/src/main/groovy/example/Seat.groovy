package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Seat {

    @Id
    @GeneratedValue(GeneratedValue.Type.SEQUENCE)
    Long id
    String flightId
    String seatId
    String customerId
    @Nullable
    String status

    Seat(String flightId, String seatId, String customerId) {
        this.flightId = flightId
        this.seatId = seatId
        this.customerId = customerId
    }
}
