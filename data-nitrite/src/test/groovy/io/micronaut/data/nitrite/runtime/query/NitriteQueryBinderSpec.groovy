package io.micronaut.data.nitrite.runtime.query

import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteQueryBinderSpec extends Specification {

    @Inject
    Nitrite database

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
            conversionService, objectMapper, database.getConfig().nitriteMapper(), runtimeEntityRegistry
        )
        NitriteQueryBinder binder = new NitriteQueryBinder(entityMapper, database)
        Document doc = Document.createDocument("firstName", "John")
        Map map = ["firstName": "Jane"]

        expect:
        binder.readSegmentValue(doc, "firstName") == "John"
        binder.readSegmentValue(doc, "first_name") == "John"
        binder.readSegmentValue(map, "firstName") == "Jane"
        binder.readSegmentValue(map, "first_name") == "Jane"
        binder.readSegmentValue(null, "foo") == null

        when:
        def bean = new MyBean(firstName: "Alice")

        then:
        binder.readSegmentValue(bean, "firstName") == "Alice"
        binder.readSegmentValue(bean, "first_name") == "Alice"
    }
}

class MyBean {
    String firstName
}
