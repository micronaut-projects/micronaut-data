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
    UUID id

    String orderNumber

    @Relation(Relation.Kind.MANY_TO_ONE)
    Widget widget

    WidgetOrder() {
    }

    WidgetOrder(String orderNumber, Widget widget) {
        this.orderNumber = orderNumber
        this.widget = widget
    }
}
// end::uuid-widget-order[]
