package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;

@MappedEntity
public class CompositeIdCollectionChild {

    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns({
        @JoinColumn(name = "parent_tenant_id", referencedColumnName = "tenantId"),
        @JoinColumn(name = "parent_ref_id", referencedColumnName = "refId")
    })
    private CompositeIdCollectionParent parent;

    public CompositeIdCollectionChild() {
    }

    public CompositeIdCollectionChild(String name, String tenantId, String refId) {
        this.name = name;
        this.parent = new CompositeIdCollectionParent(tenantId, refId, null);
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

    public CompositeIdCollectionParent getParent() {
        return parent;
    }

    public void setParent(CompositeIdCollectionParent parent) {
        this.parent = parent;
    }
}
