package io.micronaut.data.model.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.HashSet;
import java.util.Set;

@MappedEntity(value = "cars", schema = "ford")
public class MappedEntityCar {

    @GeneratedValue
    @Id
    private Long id;

    @Nullable
    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private Set<MappedEntityCarPart> parts = new HashSet<>();

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

    public Set<MappedEntityCarPart> getParts() {
        return parts;
    }

    public void setParts(Set<MappedEntityCarPart> parts) {
        this.parts = parts;
    }
}
