package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.UUID;

// tag::uuid-widget-order[]
@MappedEntity
public class WidgetOrder {
    @Id
    @GeneratedValue
    private UUID id;

    private String orderNumber;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Widget widget;
    // end::uuid-widget-order[]

    public WidgetOrder() {
    }

    // tag::uuid-widget-order[]
    public WidgetOrder(String orderNumber, Widget widget) {
        this.orderNumber = orderNumber;
        this.widget = widget;
    }
    // end::uuid-widget-order[]

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Widget getWidget() {
        return widget;
    }

    public void setWidget(Widget widget) {
        this.widget = widget;
    }
// tag::uuid-widget-order[]
}
// end::uuid-widget-order[]
