package io.micronaut.data.tck.tests.metamodel

import io.micronaut.core.naming.NameUtils
import io.micronaut.data.tck.metamodel.ExpectedMetamodel
import jakarta.persistence.metamodel.MapAttribute
import spock.lang.Specification

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
                    assert false: "Field '${f}' should not exist in ${metamodelClass.name}"
                } catch (NoSuchFieldException ignored) {
                }
            }
        }

        attributes.each { ExpectedMetamodel.Attribute attribute ->
            def fieldName = attribute.constantName()
            def field = metamodelClass.getField(fieldName)

            assert field.type == attribute.attributeType()
            assert field.getProperties()["genericType"]["actualTypeArguments"][0].canonicalName == attribute.declaringType()

            if (attribute.attributeType() == MapAttribute) {
                assert attribute.fieldTypes().size() == 2
                assert field.getProperties()["genericType"]["actualTypeArguments"][1].canonicalName == attribute.fieldTypes().first
                assert field.getProperties()["genericType"]["actualTypeArguments"][2].canonicalName == attribute.fieldTypes().last
            } else {
                assert attribute.fieldTypes().size() == 1
                def fieldType = attribute.fieldTypes().first
                if (fieldType in [Set, List, Collection, Map]) {
                    assert field.getProperties()["genericType"]["actualTypeArguments"][1].rawType.canonicalName == fieldType.getName()
                } else {
                    assert field.getProperties()["genericType"]["actualTypeArguments"][1].canonicalName == fieldType.canonicalName
                }
            }
        }
    }

}
