package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.Nose
import io.micronaut.data.tck.entities.Nose_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class NoseMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def NOSE_CLASS_NAME = Nose.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Nose,
                Nose_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], NOSE_CLASS_NAME),
                        new Attribute("face", SingularAttribute, [Face], NOSE_CLASS_NAME),
                ),
                List.of()
        )
    }
}
