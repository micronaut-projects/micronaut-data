package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Pet {

    private Long id;
    private String name;
    private PetType type;
    private Owner owner;

    Pet() {
    }

    public Pet(Long id, String name, @Nullable PetType type, Owner owner) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = (Owner) owner;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        this.type = type;
    }

    @Introspected
    enum PetType {
        DOG,
        CAT
    }
}
