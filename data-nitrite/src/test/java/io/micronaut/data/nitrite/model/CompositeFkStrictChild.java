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
 * Mirrors a Kotlin entity that declares its association as a non-null constructor parameter: the mapper
 * has to supply the joined association at construction time, because there is no writable property to
 * populate afterwards.
 */
@MappedEntity
public class CompositeFkStrictChild {

    @Id
    @GeneratedValue
    private String id;

    private final String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns({
        @JoinColumn(name = "fk_tenant_id", referencedColumnName = "tenantId"),
        @JoinColumn(name = "fk_ref_id", referencedColumnName = "refId")
    })
    private final CompositeFkParent parent;

    @Creator
    public CompositeFkStrictChild(@NonNull String name, @NonNull CompositeFkParent parent) {
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

    public CompositeFkParent getParent() {
        return parent;
    }
}
