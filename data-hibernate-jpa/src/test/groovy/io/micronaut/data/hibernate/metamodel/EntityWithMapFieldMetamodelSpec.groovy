package io.micronaut.data.hibernate.metamodel

import io.micronaut.data.hibernate.entities.EntityWithMapField
import io.micronaut.data.hibernate.entities.EntityWithMapField_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.CollectionAttribute
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.MapAttribute
import jakarta.persistence.metamodel.SetAttribute
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute;

class EntityWithMapFieldMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def ENTITY_CLASS_NAME = EntityWithMapField.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                EntityWithMapField,
                EntityWithMapField_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], ENTITY_CLASS_NAME),
                        new Attribute("properties", MapAttribute, [String, String], ENTITY_CLASS_NAME),
                        new Attribute("tagsSet", SetAttribute, [String], ENTITY_CLASS_NAME),
                        new Attribute("tagsCollection", CollectionAttribute, [String], ENTITY_CLASS_NAME),
                ),
                List.of()
        )
    }
}
