package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

@MappedEntity
public class OneToManyParent {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent")
    private List<OneToManyChild> children = new ArrayList<>();

    public OneToManyParent() {
    }

    public OneToManyParent(String name) {
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

    public List<OneToManyChild> getChildren() {
        return children;
    }

    public void setChildren(List<OneToManyChild> children) {
        this.children = children;
    }
}
