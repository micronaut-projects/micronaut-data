package io.micronaut.metamodel

import io.micronaut.data.tck.entities.EmbeddableClass
import io.micronaut.data.tck.entities.OrderPk
import io.micronaut.data.tck.entities.PurchaseOrder
import io.micronaut.data.tck.entities.PurchaseOrder_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.*

class PurchaseOrderMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def PURCHASE_ORDER_CLASS_NAME = PurchaseOrder.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                PurchaseOrder,
                PurchaseOrder_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [OrderPk], PURCHASE_ORDER_CLASS_NAME),
                        new Attribute("description", SingularAttribute, [String], PURCHASE_ORDER_CLASS_NAME),
                        new Attribute("details", SingularAttribute, [EmbeddableClass], PURCHASE_ORDER_CLASS_NAME),
                ),
                List.of()
        )
    }
}
