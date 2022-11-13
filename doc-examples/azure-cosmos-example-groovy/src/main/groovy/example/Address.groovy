package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Embeddable
class Address {

    String state;
    String county;
    String city;

}
