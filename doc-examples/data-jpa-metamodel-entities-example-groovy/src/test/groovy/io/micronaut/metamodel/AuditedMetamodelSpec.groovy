package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Audited
import io.micronaut.data.tck.entities.Audited_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.MappedSuperclassType
import jakarta.persistence.metamodel.SingularAttribute

import java.time.Instant

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class AuditedMetamodelSpec extends AbstractEntityMetamodelSpec {

    final static def AUDITED_CLASS_NAME = Audited.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Audited,
                Audited_,
                MappedSuperclassType,
                List.of(
                        new Attribute("createdAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("updatedAt", SingularAttribute, [Instant], AUDITED_CLASS_NAME),
                        new Attribute("version", SingularAttribute, [Long], AUDITED_CLASS_NAME),
                ),
                List.of()
        )
    }
}
