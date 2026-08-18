package io.micronaut.data.nitrite.runtime.query

import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.collection.Document
import spock.lang.Specification

import java.util.function.Function

@MicronautTest(transactional = false)
class NitriteQueryBinderSpec extends Specification {

    @Inject
    io.micronaut.data.model.runtime.RuntimeEntityRegistry runtimeEntityRegistry
    @Inject
    io.micronaut.data.runtime.convert.DataConversionService conversionService
    @Inject
    io.micronaut.data.model.runtime.AttributeConverterRegistry attributeConverterRegistry
    @Inject
    io.micronaut.serde.ObjectMapper objectMapper

    void "test readSegmentValue"() {
        given:
        NitriteEntityMapper entityMapper = new NitriteEntityMapper(
            conversionService, objectMapper, runtimeEntityRegistry
        )
        NitriteQueryBinder binder = new NitriteQueryBinder(entityMapper)
        Document doc = Document.createDocument("firstName", "John")
        Map map = ["firstName": "Jane"]

        expect:
        binder.readSegmentValue(doc, "firstName") == "John"
        binder.readSegmentValue(doc, "first_name") == "John"
        binder.readSegmentValue(map, "firstName") == "Jane"
        binder.readSegmentValue(map, "first_name") == "Jane"
        binder.readSegmentValue(null, "foo") == null

        when:
        def bean = new NamedBean(firstName: "Alice")

        then:
        binder.readSegmentValue(bean, "firstName") == "Alice"
        binder.readSegmentValue(bean, "first_name") == "Alice"
    }

    void "a value that is not a positional placeholder has no index"() {
        expect:
        NitriteQueryBinder.extractPlaceholderIndex(null) == null
        NitriteQueryBinder.extractPlaceholderIndex("plain string") == null
        NitriteQueryBinder.extractPlaceholderIndex('$mn_qp:not-a-number') == null

        and: "both placeholder encodings carry the same index"
        NitriteQueryBinder.extractPlaceholderIndex('$mn_qp:2') == 2
        NitriteQueryBinder.extractPlaceholderIndex(['$mn_qp': 2]) == 2
    }

    void "placeholders resolve against the parameters they name"() {
        given:
        def identity = { it } as Function

        expect: "a named parameter resolves from the named map"
        NitriteQueryBinder.resolveParameterValue(":p1", null, ["p1": "val"], identity) == "val"

        and: "both positional encodings resolve from the JSON parameter array"
        NitriteQueryBinder.resolveParameterValue('$mn_qp:0', [1] as Object[], [:], identity) == 1
        NitriteQueryBinder.resolveParameterValue(['$mn_qp': 0], [1] as Object[], [:], identity) == 1

        and: "an unresolvable placeholder still goes through the converter, as a null"
        NitriteQueryBinder.resolveParameterValue(":missing", null, [:], identity) == null

        and: "a value that is not a placeholder is returned untouched"
        NitriteQueryBinder.resolveParameterValue("plain string", [1] as Object[], [:], identity) == "plain string"
    }
}

class NamedBean {
    String firstName
}
