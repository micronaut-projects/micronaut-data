package io.micronaut.data.hibernate.entities;

import javax.persistence.*;
import java.util.UUID;

@Entity
public class Pet {

    @Id
    @GeneratedValue
    private UUID id;
    private String name;
    @Enumerated(EnumType.STRING)
    private Pet.PetType type = Pet.PetType.DOG;

    public void setName(String name) {
        this.name = name;
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

