package io.micronaut.metamodel

import io.micronaut.data.tck.entities.AuditedVehicle
import io.micronaut.data.tck.entities.AuditedVehicle_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import java.time.Instant

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute
import static io.micronaut.metamodel.AuditedMetamodelSpec.getAUDITED_CLASS_NAME

class AuditedVehicleMetamodelSpec extends AbstractEntityMetamodelSpec {

    final static def AUDITED_VEHICLE_CLASS_NAME = AuditedVehicle.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                AuditedVehicle,
                AuditedVehicle_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], AUDITED_VEHICLE_CLASS_NAME),
                        new Attribute("vin", SingularAttribute, [String], AUDITED_VEHICLE_CLASS_NAME),
                        new Attribute("createdAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("updatedAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("version", SingularAttribute, [Long], AUDITED_CLASS_NAME),
                ),
                List.of()
        )
    }
}
