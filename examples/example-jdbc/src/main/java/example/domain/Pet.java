package example.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.AutoPopulated;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.UUID;

@Entity
public class Pet {

    @Id
    @AutoPopulated
    private UUID id;
    private String name;
    @ManyToOne
    private Owner owner;
    private PetType type = PetType.DOG;

    @JsonCreator
    public Pet(@JsonProperty("name") String name, @JsonProperty("owner") Owner owner) {
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