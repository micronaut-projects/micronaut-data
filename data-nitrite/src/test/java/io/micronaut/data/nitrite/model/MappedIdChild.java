package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

/**
 * Inverse side of {@link MappedIdParent}; its identity also carries a mapped name.
 */
@MappedEntity
public class MappedIdChild {

    @Id
    @GeneratedValue
    @MappedProperty("child_id")
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private MappedIdParent parent;

    public MappedIdChild() {
    }

    public MappedIdChild(String name, MappedIdParent parent) {
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

    public MappedIdParent getParent() {
        return parent;
    }

    public void setParent(MappedIdParent parent) {
        this.parent = parent;
    }
}
