package io.micronaut.data.nitrite.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;

import java.util.Objects;

/**
 * Mirrors a Kotlin entity that exposes its association as a mutable property but takes it as a
 * non-nullable constructor parameter: the property could be populated after instantiation, yet the
 * constructor still rejects a null association, so the mapper cannot defer it.
 */
@MappedEntity
public class CompositeFkRequiredChild {

    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns({
        @JoinColumn(name = "fk_tenant_id", referencedColumnName = "tenantId"),
        @JoinColumn(name = "fk_ref_id", referencedColumnName = "refId")
    })
    private CompositeFkParent parent;

    @Creator
    public CompositeFkRequiredChild(@NonNull String name, @NonNull CompositeFkParent parent) {
        this.name = Objects.requireNonNull(name, "name");
        this.parent = Objects.requireNonNull(parent, "parent");
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

    public CompositeFkParent getParent() {
        return parent;
    }

    public void setParent(CompositeFkParent parent) {
        this.parent = parent;
    }
}
