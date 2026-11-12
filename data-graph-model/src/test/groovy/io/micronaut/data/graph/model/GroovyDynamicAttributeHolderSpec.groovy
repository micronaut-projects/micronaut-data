package io.micronaut.data.graph.model

import spock.lang.Specification

/**
 * Checks the Groovy syntax for schemaless properties.
 */
class GroovyDynamicAttributeHolderSpec extends Specification {

    void "subscripts and undeclared properties read and write the schemaless properties"() {
        given:
        def pet = new Pet()

        when:
        pet['legs'] = 4
        pet.goesInPatch = true

        then:
        pet['goesInPatch'] == true
        pet.legs == 4
        pet.get('legs', Integer) == 4
        pet.attributes == [legs: 4, goesInPatch: true]

        when:
        pet.legs = null

        then:
        pet['legs'] == null
        pet.attributes.containsKey('legs')
    }

    static class Pet implements GroovyDynamicAttributeHolder {
        Map<String, Object> attributes = [:]

        @Override
        Map<String, Object> dynamicAttributes() {
            attributes
        }
    }
}
