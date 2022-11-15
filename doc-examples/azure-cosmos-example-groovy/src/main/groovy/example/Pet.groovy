package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Pet {

    String givenName;
    PetType type;

}

enum PetType {
    DOG, CAT, HAMSTER
}
