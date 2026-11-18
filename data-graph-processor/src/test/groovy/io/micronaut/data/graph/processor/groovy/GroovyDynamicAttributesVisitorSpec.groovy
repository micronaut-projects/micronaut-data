package io.micronaut.data.graph.processor.groovy

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.data.graph.model.DynamicAttributeHolder
import io.micronaut.data.graph.model.GroovyDynamicAttributeHolder

class GroovyDynamicAttributesVisitorSpec extends AbstractBeanDefinitionSpec {

    void "Groovy nodes holding schemaless properties get property and subscript syntax"() {
        when:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.DynamicAttributes
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.model.DynamicAttributeHolder

@Node
class Pet implements DynamicAttributeHolder {
    @Id
    String name
    @DynamicAttributes
    Map<String, Object> attributes = [:]

    Map<String, Object> dynamicAttributes() {
        attributes
    }
}
''')
        def type = classLoader.loadClass('test.Pet')
        def pet = type.getDeclaredConstructor().newInstance()
        pet.name = 'Rex'
        pet.colour = 'brown'
        pet['age'] = 3

        then:
        GroovyDynamicAttributeHolder.isAssignableFrom(type)
        pet.name == 'Rex'
        pet.attributes == [colour: 'brown', age: 3]
        pet.colour == 'brown'
        pet['age'] == 3
    }

    void "Groovy nodes without schemaless properties are unchanged"() {
        when:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.Node

@Node
class Tag {
    @Id
    String name
}
''')
        def type = classLoader.loadClass('test.Tag')

        then:
        !DynamicAttributeHolder.isAssignableFrom(type)
    }

    void "Groovy nodes that already implement the Groovy holder keep it once"() {
        when:
        def classLoader = buildClassLoader('''
package test

import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.DynamicAttributes
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.model.GroovyDynamicAttributeHolder

@Node
class Gadget implements GroovyDynamicAttributeHolder {
    @Id
    String name
    @DynamicAttributes
    Map<String, Object> attributes = [:]

    Map<String, Object> dynamicAttributes() {
        attributes
    }
}
''')
        def type = classLoader.loadClass('test.Gadget')

        then:
        type.interfaces.count { it == GroovyDynamicAttributeHolder } == 1
    }
}
