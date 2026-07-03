package io.micronaut.metamodel

import io.micronaut.data.tck.entities.CardPayment
import io.micronaut.data.tck.entities.CardPayment_
import io.micronaut.data.tck.entities.Money
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import java.time.Instant

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute
import static io.micronaut.metamodel.AuditedMetamodelSpec.getAUDITED_CLASS_NAME
import static io.micronaut.metamodel.PaymentMetamodelSpec.getPAYMENT_CLASS_NAME;

class CardPaymentMetamodelSpec extends AbstractEntityMetamodelSpec {

    final static def CARD_CLASS_NAME = CardPayment.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                CardPayment,
                CardPayment_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], PAYMENT_CLASS_NAME),
                        new Attribute("reference", SingularAttribute, [String], PAYMENT_CLASS_NAME),
                        new Attribute("total", SingularAttribute, [Money], PAYMENT_CLASS_NAME),
                        new Attribute("createdAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("updatedAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("version", SingularAttribute, [Long], AUDITED_CLASS_NAME),
                        new Attribute("cardLast4", SingularAttribute, [String], CARD_CLASS_NAME),
                ),
                List.of()
        )
    }
}
