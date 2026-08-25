package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

/**
 * Owning side of a one-to-many whose identity carries a mapped name.
 */
@MappedEntity
public class MappedIdParent {

    @Id
    @GeneratedValue
    @MappedProperty("parent_id")
    private String id;

    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent")
    private List<MappedIdChild> children = new ArrayList<>();

    public MappedIdParent() {
    }

    public MappedIdParent(String name) {
        this.name = name;
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

    public List<MappedIdChild> getChildren() {
        return children;
    }

    public void setChildren(List<MappedIdChild> children) {
        this.children = children;
    }
}
