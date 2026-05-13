package io.micronaut.metamodel

import io.micronaut.data.tck.entities.EmbeddableClass
import io.micronaut.data.tck.entities.EmbeddedOwner
import io.micronaut.data.tck.entities.EmbeddedOwner_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class EmbeddedOwnerMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def EMBEDDED_OWNER_CLASS_NAME = EmbeddedOwner.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                EmbeddedOwner,
                EmbeddedOwner_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], EMBEDDED_OWNER_CLASS_NAME),
                        new Attribute("ownerName", SingularAttribute, [String], EMBEDDED_OWNER_CLASS_NAME),
                        new Attribute("embedded", SingularAttribute, [EmbeddableClass], EMBEDDED_OWNER_CLASS_NAME),
                ),
                List.of()
        )
    }
}
