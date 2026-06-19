package io.micronaut.metamodel

import io.micronaut.data.tck.entities.EmployeeId
import io.micronaut.data.tck.entities.EmployeeMixedAccessEmbeddedId
import io.micronaut.data.tck.entities.EmployeeMixedAccessEmbeddedId_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute
import spock.lang.Ignore

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

@Ignore("Access annotation not supported currently")
class EmployeeMixedAccessEmbeddedIdMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def EMPLOYEE_MIXED_ACCESS_EMBEDDED_ID_CLASS_NAME = EmployeeMixedAccessEmbeddedId.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                EmployeeMixedAccessEmbeddedId,
                EmployeeMixedAccessEmbeddedId_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [EmployeeId], EMPLOYEE_MIXED_ACCESS_EMBEDDED_ID_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], EMPLOYEE_MIXED_ACCESS_EMBEDDED_ID_CLASS_NAME),
                        new Attribute("salary", SingularAttribute, [Double], EMPLOYEE_MIXED_ACCESS_EMBEDDED_ID_CLASS_NAME),
                        new Attribute("fieldAnnotated", SingularAttribute, [String], EMPLOYEE_MIXED_ACCESS_EMBEDDED_ID_CLASS_NAME),
                ),
                List.of("fieldWithoutAccessors")
        )
    }
}
