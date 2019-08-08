package example.domain

import io.micronaut.core.annotation.Creator
import io.micronaut.data.annotation.AutoPopulated

import javax.annotation.Nullable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne

@Entity
class Pet {

    @Id
    @AutoPopulated
    UUID id
    PetType type = PetType.DOG
    private String name
    @ManyToOne
    private Owner owner

    @Creator
    Pet(String name, @Nullable Owner owner) {
        this.name = name
        this.owner = owner
    }

    String getName() {
        return name
    }

    Owner getOwner() {
        return owner
    }
}