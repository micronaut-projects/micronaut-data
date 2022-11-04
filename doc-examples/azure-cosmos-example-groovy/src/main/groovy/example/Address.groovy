package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Embeddable
class Address {

    private String state;
    private String county;
    private String city;

    String getState() {
        return state
    }

    void setState(String state) {
        this.state = state
    }

    String getCounty() {
        return county
    }

    void setCounty(String county) {
        this.county = county
    }

    String getCity() {
        return city
    }

    void setCity(String city) {
        this.city = city
    }
}
