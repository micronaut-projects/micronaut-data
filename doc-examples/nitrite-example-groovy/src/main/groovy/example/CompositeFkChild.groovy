package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.JoinColumn
import io.micronaut.data.annotation.sql.JoinColumns

// tag::composite-fk-child[]
@MappedEntity
class CompositeFkChild {
    @Id
    @GeneratedValue
    String id

    String name

    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumns([
        @JoinColumn(name = "fk_tenant_id", referencedColumnName = "tenantId"),
        @JoinColumn(name = "fk_ref_id", referencedColumnName = "refId")
    ])
    CompositeFkParent parent

    CompositeFkChild() {
    }

    CompositeFkChild(String name, CompositeFkParent parent) {
        this.name = name
        this.parent = parent
    }
}
// end::composite-fk-child[]
