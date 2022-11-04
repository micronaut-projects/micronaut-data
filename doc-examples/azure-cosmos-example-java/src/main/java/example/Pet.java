package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Pet {

    private String givenName;

    private String type;

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
