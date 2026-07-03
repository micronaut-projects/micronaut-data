package io.micronaut.metamodel

import io.micronaut.data.tck.entities.EmbeddableClass
import io.micronaut.data.tck.entities.EmbeddableClass_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EmbeddableType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class EmbeddableClassMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def EMBEDDABLE_CLASS_NAME = EmbeddableClass.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                EmbeddableClass,
                EmbeddableClass_,
                EmbeddableType,
                List.of(
                        new Attribute("embeddedName", SingularAttribute, [String], EMBEDDABLE_CLASS_NAME),
                        new Attribute("number", SingularAttribute, [Long], EMBEDDABLE_CLASS_NAME),
                        new Attribute("n", SingularAttribute, [Long], EMBEDDABLE_CLASS_NAME),
                        new Attribute("d", SingularAttribute, [Double], EMBEDDABLE_CLASS_NAME),
                ),
                List.of()
        )
    }
}
