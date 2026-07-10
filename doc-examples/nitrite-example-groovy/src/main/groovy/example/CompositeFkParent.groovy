package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::composite-fk-parent[]
@MappedEntity
class CompositeFkParent {
    @Id
    @GeneratedValue
    String id

    String tenantId
    Long refId

    CompositeFkParent() {
    }

    CompositeFkParent(String tenantId, Long refId) {
        this.tenantId = tenantId
        this.refId = refId
    }
}
// end::composite-fk-parent[]
