package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Truck
import io.micronaut.data.tck.entities.Truck_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import java.time.Instant

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute
import static io.micronaut.metamodel.AuditedMetamodelSpec.getAUDITED_CLASS_NAME
import static io.micronaut.metamodel.AuditedVehicleMetamodelSpec.getAUDITED_VEHICLE_CLASS_NAME;

class TruckMetamodelSpec extends AbstractEntityMetamodelSpec {

    final static def TRUCK_CLASS_NAME = Truck.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Truck,
                Truck_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], AUDITED_VEHICLE_CLASS_NAME),
                        new Attribute("vin", SingularAttribute, [String], AUDITED_VEHICLE_CLASS_NAME),
                        new Attribute("createdAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("updatedAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("version", SingularAttribute, [Long], AUDITED_CLASS_NAME),
                        new Attribute("maxLoad", SingularAttribute, [Double], TRUCK_CLASS_NAME),
                ),
                List.of()
        )
    }
}
