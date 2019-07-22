package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.*;

import java.util.UUID;

@MappedEntity
public class Pet {

    @Id
    @AutoPopulated
    private UUID id;
    private String name;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Owner owner;
    private PetType type = PetType.DOG;

    @Creator
    public Pet(String name, Owner owner) {
        this.name = name;
        this.owner = owner;
    }

    public Owner getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        this.type = type;
    }

    public void setId(UUID id) {
        this.id = id;
    }


    public enum PetType {
        DOG,
        CAT
    }
}
