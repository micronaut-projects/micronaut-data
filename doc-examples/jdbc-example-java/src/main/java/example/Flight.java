package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

// tag::upsert-entity[]
@MappedEntity
public class Flight {

    @Id
    private final String number;

    private String origin;

    private String destination;

    public Flight(String number, String origin, String destination) {
        this.number = number;
        this.origin = origin;
        this.destination = destination;
    }

    public String getNumber() {
        return number;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }
}
// end::upsert-entity[]
