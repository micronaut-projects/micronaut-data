package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Money
import io.micronaut.data.tck.entities.Payment
import io.micronaut.data.tck.entities.Payment_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import java.time.Instant

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.*
import static io.micronaut.metamodel.AuditedMetamodelSpec.AUDITED_CLASS_NAME

class PaymentMetamodelSpec extends AbstractEntityMetamodelSpec {

    final static def PAYMENT_CLASS_NAME = Payment.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Payment,
                Payment_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], PAYMENT_CLASS_NAME),
                        new Attribute("reference", SingularAttribute, [String], PAYMENT_CLASS_NAME),
                        new Attribute("total", SingularAttribute, [Money], PAYMENT_CLASS_NAME),
                        new Attribute("createdAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("updatedAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("version", SingularAttribute, [Long], AUDITED_CLASS_NAME),
                ),
                List.of()
        )
    }
}
