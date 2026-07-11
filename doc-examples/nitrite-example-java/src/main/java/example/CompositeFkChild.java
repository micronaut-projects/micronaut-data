package example;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;

// tag::composite-fk-child[]
@MappedEntity
public class CompositeFkChild {
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
    // end::composite-fk-child[]

    public CompositeFkChild() {
    }

    // tag::composite-fk-child[]
    @Creator // <1>
    public CompositeFkChild(String name, CompositeFkParent parent) {
        this.name = name;
        this.parent = parent;
    }
    // end::composite-fk-child[]

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
// tag::composite-fk-child[]
}
// end::composite-fk-child[]
