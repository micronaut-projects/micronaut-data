package io.micronaut.metamodel

import io.micronaut.data.tck.entities.EmployeeFieldAccess
import io.micronaut.data.tck.entities.EmployeeFieldAccess_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute
import spock.lang.Ignore

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

@Ignore("Access annotation not supported currently")
class EmployeeFieldAccessMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def EMPLOYEE_FIELD_ACCESS_CLASS_NAME = EmployeeFieldAccess.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                EmployeeFieldAccess,
                EmployeeFieldAccess_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], EMPLOYEE_FIELD_ACCESS_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], EMPLOYEE_FIELD_ACCESS_CLASS_NAME),
                        new Attribute("salary", SingularAttribute, [Double], EMPLOYEE_FIELD_ACCESS_CLASS_NAME),
                        new Attribute("noAccessors", SingularAttribute, [String], EMPLOYEE_FIELD_ACCESS_CLASS_NAME),
                ),
                List.of()
        )
    }
}
