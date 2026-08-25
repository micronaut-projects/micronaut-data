package io.micronaut.data.nitrite.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;

/**
 * Child of an entity whose identity is composite. Unlike {@link CompositeFkChild}, whose parent
 * carries a single {@code @Id} alongside the referenced columns, {@link CompositeIdEntity} has no
 * single id property at all, so the association resolves no associated id property and depends
 * entirely on its mapped join columns.
 */
@MappedEntity
public class CompositeIdChild {

    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns({
        @JoinColumn(name = "fk_tenant_id", referencedColumnName = "tenantId"),
        @JoinColumn(name = "fk_ref_id", referencedColumnName = "refId")
    })
    private CompositeIdEntity parent;

    public CompositeIdChild() {
    }

    @Creator
    public CompositeIdChild(String name, CompositeIdEntity parent) {
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

    public CompositeIdEntity getParent() {
        return parent;
    }

    public void setParent(CompositeIdEntity parent) {
        this.parent = parent;
    }
}
