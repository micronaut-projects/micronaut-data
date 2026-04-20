package io.micronaut.metamodel

import io.micronaut.data.tck.entities.ArraysEntity
import io.micronaut.data.tck.entities.ArraysEntity_
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import io.micronaut.data.tck.tests.metamodel.AbstractEntityMetamodelSpec
import jakarta.persistence.metamodel.CollectionAttribute
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.SingularAttribute

import static io.micronaut.data.tck.metamodel.ExpectedMetamodel.Attribute

class ArraysEntityMetamodelSpec extends AbstractEntityMetamodelSpec {

    final def ARRAYS_ENTITY_CLASS_NAME = ArraysEntity.name

    @Override
    ExpectedMetamodel getExpectedMetamodel() {
        return new ExpectedMetamodel(
                ArraysEntity,
                ArraysEntity_,
                EntityType,
                List.of(
                        new Attribute("someId", SingularAttribute, [Long], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("stringArray", SingularAttribute, [String[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("stringArrayCollection", CollectionAttribute, [String], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("shortArray", SingularAttribute, [Short[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("shortPrimitiveArray", SingularAttribute, [short[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("shortArrayCollection", CollectionAttribute, [Short], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("integerArray", SingularAttribute, [Integer[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("integerPrimitiveArray", SingularAttribute, [int[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("integerArrayCollection", CollectionAttribute, [Integer], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("longArray", SingularAttribute, [Long[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("longPrimitiveArray", SingularAttribute, [long[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("longArrayCollection", CollectionAttribute, [Long], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("floatArray", SingularAttribute, [Float[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("floatPrimitiveArray", SingularAttribute, [float[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("floatArrayCollection", CollectionAttribute, [Float], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("doubleArray", SingularAttribute, [Double[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("doublePrimitiveArray", SingularAttribute, [double[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("doubleArrayCollection", CollectionAttribute, [Double], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("characterArray", SingularAttribute, [Character[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("characterPrimitiveArray", SingularAttribute, [char[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("characterArrayCollection", CollectionAttribute, [Character], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("booleanArray", SingularAttribute, [Boolean[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("booleanPrimitiveArray", SingularAttribute, [boolean[]], ARRAYS_ENTITY_CLASS_NAME),
                        new Attribute("booleanArrayCollection", CollectionAttribute, [Boolean], ARRAYS_ENTITY_CLASS_NAME),
                ),
                List.of()
        )
    }
}
