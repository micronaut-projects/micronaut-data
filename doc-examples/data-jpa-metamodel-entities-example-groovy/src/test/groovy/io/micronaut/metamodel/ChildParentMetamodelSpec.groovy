package io.micronaut.metamodel

import io.micronaut.data.tck.entities.Child
import io.micronaut.data.tck.entities.Child_
import io.micronaut.data.tck.entities.Parent
import io.micronaut.data.tck.entities.Parent_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.MappedSuperclassType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class ChildMetamodelSpec extends AbstractEntityMetamodelSpec {

    final static def CHILD_CLASS_NAME = Child.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Child,
                Child_,
                EntityType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], ParentMetamodelSpec.PARENT_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], ParentMetamodelSpec.PARENT_CLASS_NAME),
                        new Attribute("age", SingularAttribute, [Long], CHILD_CLASS_NAME),
                ),
                List.of()
        )
    }
}

class ParentMetamodelSpec extends AbstractEntityMetamodelSpec {

    final static def PARENT_CLASS_NAME = Parent.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                Parent,
                Parent_,
                MappedSuperclassType,
                List.of(
                        new Attribute("id", SingularAttribute, [Long], PARENT_CLASS_NAME),
                        new Attribute("name", SingularAttribute, [String], PARENT_CLASS_NAME),
                ),
                List.of('age')
        )
    }
}
