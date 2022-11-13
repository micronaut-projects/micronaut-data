package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class Pet(
    var givenName: String,
    var type: PetType
)

enum class PetType {
    DOG, CAT, HAMSTER
}
