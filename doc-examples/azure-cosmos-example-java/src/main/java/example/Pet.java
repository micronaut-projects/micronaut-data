package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Pet {

    private String givenName;

    private PetType type;

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        this.type = type;
    }
}

enum PetType {
    DOG, CAT, HAMSTER
}
