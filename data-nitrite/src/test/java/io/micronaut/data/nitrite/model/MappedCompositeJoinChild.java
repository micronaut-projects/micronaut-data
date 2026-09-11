package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;

@MappedEntity
public class MappedCompositeJoinChild {

    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns({
        @JoinColumn(name = "parent_tenant_key", referencedColumnName = "tenant_key"),
        @JoinColumn(name = "parent_reference_key", referencedColumnName = "reference_key"),
        @JoinColumn(name = "parent_name_key", referencedColumnName = "name_key")
    })
    private MappedCompositeJoinParent parent;

    public MappedCompositeJoinChild() {
    }

    public MappedCompositeJoinChild(String name, MappedCompositeJoinParent parent) {
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

    public MappedCompositeJoinParent getParent() {
        return parent;
    }

    public void setParent(MappedCompositeJoinParent parent) {
        this.parent = parent;
    }
}
