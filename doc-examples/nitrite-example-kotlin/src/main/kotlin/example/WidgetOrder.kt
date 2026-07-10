package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import java.util.UUID

// tag::uuid-widget-order[]
@MappedEntity
class WidgetOrder {
    @Id
    @GeneratedValue
    var id: UUID? = null

    var orderNumber: String? = null

    @Relation(Relation.Kind.MANY_TO_ONE)
    var widget: Widget? = null

    constructor()

    constructor(orderNumber: String, widget: Widget) {
        this.orderNumber = orderNumber
        this.widget = widget
    }
}
// end::uuid-widget-order[]
