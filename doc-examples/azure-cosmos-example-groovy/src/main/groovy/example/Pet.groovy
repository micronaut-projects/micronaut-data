package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Pet {

    private String givenName;
    private String type;

    String getGivenName() {
        return givenName
    }

    void setGivenName(String givenName) {
        this.givenName = givenName
    }

    String getType() {
        return type
    }

    void setType(String type) {
        this.type = type
    }
}
