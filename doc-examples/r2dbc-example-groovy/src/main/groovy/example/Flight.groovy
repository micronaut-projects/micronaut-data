package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Flight {

    @Id
    final String number

    String origin

    String destination

    Flight(String number, String origin, String destination) {
        this.number = number
        this.origin = origin
        this.destination = destination
    }
}
