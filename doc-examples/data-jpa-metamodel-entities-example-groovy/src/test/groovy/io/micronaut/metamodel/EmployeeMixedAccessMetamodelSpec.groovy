package io.micronaut.metamodel

import io.micronaut.data.tck.entities.EmployeeMixedAccess
import io.micronaut.data.tck.entities.EmployeeMixedAccess_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class EmployeeMixedAccessMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def EMPLOYEE_MIXED_ACCESS_CLASS_NAME = EmployeeMixedAccess.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                EmployeeMixedAccess,
                EmployeeMixedAccess_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], EMPLOYEE_MIXED_ACCESS_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], EMPLOYEE_MIXED_ACCESS_CLASS_NAME),
                        new Attribute("salary", SingularAttribute, [Double], EMPLOYEE_MIXED_ACCESS_CLASS_NAME),

                        // Field-level override should be included
                        // But Access annotation on fields is not supported yet.
                        // new Attribute("fieldAnnotated", SingularAttribute, [String], EMPLOYEE_MIXED_ACCESS_CLASS_NAME),
                ),
                List.of("fieldWithoutAccessors")
        )
    }
}
