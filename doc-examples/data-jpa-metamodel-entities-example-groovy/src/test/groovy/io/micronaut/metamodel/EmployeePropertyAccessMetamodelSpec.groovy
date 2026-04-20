package io.micronaut.metamodel

import io.micronaut.data.tck.entities.EmployeePropertyAccess
import io.micronaut.data.tck.entities.EmployeePropertyAccess_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.*

class EmployeePropertyAccessMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def EMPLOYEE_PROPERTY_ACCESS_CLASS_NAME = EmployeePropertyAccess.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                EmployeePropertyAccess,
                EmployeePropertyAccess_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], EMPLOYEE_PROPERTY_ACCESS_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], EMPLOYEE_PROPERTY_ACCESS_CLASS_NAME),
                        new Attribute("salary", SingularAttribute, [Double], EMPLOYEE_PROPERTY_ACCESS_CLASS_NAME),
                ),
                List.of("noAccessors")
        )
    }
}
