package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::composite-fk-parent[]
@MappedEntity
class CompositeFkParent {
    @Id
    @GeneratedValue
    var id: String? = null

    var tenantId: String? = null
    var refId: Long? = null

    constructor(tenantId: String, refId: Long) {
        this.tenantId = tenantId
        this.refId = refId
    }
    // end::composite-fk-parent[]

    constructor()
// tag::composite-fk-parent[]
}
// end::composite-fk-parent[]
