package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity
public class OneToManyChild {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private OneToManyParent parent;

    /**
     * A second ONE_TO_MANY on the child, used only so that a reverse-lookup path can name a
     * target property whose relation kind is not the MANY_TO_ONE inverse the resolver expects.
     */
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<OneToManyChild> siblings;

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

    public List<OneToManyChild> getSiblings() {
        return siblings;
    }

    public void setSiblings(List<OneToManyChild> siblings) {
        this.siblings = siblings;
    }
}
