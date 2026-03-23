package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public class OneToManyChild {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private OneToManyParent parent;

    public OneToManyChild() {
    }

    public OneToManyChild(String name, OneToManyParent parent) {
        this.name = name;
        this.parent = parent;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OneToManyParent getParent() {
        return parent;
    }

    public void setParent(OneToManyParent parent) {
        this.parent = parent;
    }
}
