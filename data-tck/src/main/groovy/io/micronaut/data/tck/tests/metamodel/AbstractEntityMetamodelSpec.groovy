package io.micronaut.data.tck.tests.metamodel

import io.micronaut.core.naming.NameUtils
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import spock.lang.Specification

import java.lang.reflect.ParameterizedType

import static io.micronaut.data.tck.tests.metamodel.MetamodelAssertionsUtils.assertClassFieldIsEntityType
import static io.micronaut.data.tck.tests.metamodel.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly

abstract class AbstractEntityMetamodelSpec extends Specification {

    abstract ExpectedMetamodel getExpectedMetamodel();

    void "test Entity static metaModel"() {
        given:
        def entityClass = expectedMetamodel.entityClass()
        def metamodelClass = expectedMetamodel.metamodelClass()
        def attributes = expectedMetamodel.attributes()
        def jakartaManageType = expectedMetamodel.jakartaManagedType()
        expect:
        assertMetaModelClassIsAnnotatedCorrectly(metamodelClass, entityClass)
        assertClassFieldIsEntityType(metamodelClass, jakartaManageType, entityClass)

        assert attributes.stream().allMatch { o -> metamodelClass.getField(NameUtils.environmentName(o.constantName())) != null }

        if (!expectedMetamodel.forbiddenFields().isEmpty()) {
            expectedMetamodel.forbiddenFields().each { String f ->
                try {
                    metamodelClass.getField(f)
                    assert false: "Field '${f}' should not exist in ${metamodelClass}"
                } catch (NoSuchFieldException ignored) {
                }
            }
        }

        attributes.each { ExpectedMetamodel.Attribute attribute ->
            def fieldName = attribute.constantName()
            def field = metamodelClass.getField(fieldName)

            assert field.type == attribute.attributeType()

            def fieldTypeArguments = field.getProperties()["genericType"]["actualTypeArguments"]
            assert fieldTypeArguments[0].canonicalName == attribute.declaringType()
            def fieldTypeArgs = [];

            for (int i = 1; i < fieldTypeArguments.length; i++) {
                fieldTypeArgs.addAll(flattenFieldTypeArguments(fieldTypeArguments[i]))
            }

            for (int i = 0; i < attribute.fieldTypes().size(); i++) {
                assert fieldTypeArgs.get(i) == attribute.fieldTypes().get(i).canonicalName
            }
        }
    }

    List<String> flattenFieldTypeArguments(fieldTypeArguments) {
        List<String> types = new ArrayList<>();
        if (fieldTypeArguments instanceof ParameterizedType parameterizedType) {
            types.add(fieldTypeArguments.rawType.canonicalName)
            for (int i = 0; i < parameterizedType.actualTypeArguments.size(); i++) {
                types.addAll(flattenFieldTypeArguments(parameterizedType.actualTypeArguments[i]))
            }
        } else {
            types.add(fieldTypeArguments.canonicalName)
        }
        return types;
    }

}
