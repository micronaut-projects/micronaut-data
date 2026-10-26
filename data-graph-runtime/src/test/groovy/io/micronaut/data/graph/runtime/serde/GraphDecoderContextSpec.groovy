package io.micronaut.data.graph.runtime.serde

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.config.naming.PropertyNamingStrategy
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDate

class GraphDecoderContextSpec extends Specification {

    Deserializer.DecoderContext parent = Mock()
    GraphDecoderContext context = new GraphDecoderContext(parent, ConversionService.SHARED)

    void "native values are converted rather than deserialized"() {
        when:
        def temporal = context.findDeserializer(Argument.of(LocalDate))
        def amount = context.findDeserializer(Argument.of(Duration))
        def date = context.findDeserializer(Argument.of(Date))

        then:
        0 * parent.findDeserializer(_)
        context.conversionService.is(ConversionService.SHARED)
        temporal instanceof NativeValueDeserializer
        amount instanceof NativeValueDeserializer
        date instanceof NativeValueDeserializer
    }

    void "other types are deserialized by the registry, and fall back to conversion"() {
        given:
        Deserializer<String> strings = Mock()

        when:
        def found = context.findDeserializer(Argument.STRING)

        then:
        1 * parent.findDeserializer(Argument.STRING) >> strings
        found.is(strings)

        when:
        def fallback = context.findDeserializer(Argument.of(Thread))

        then:
        1 * parent.findDeserializer(_) >> { throw new SerdeException("none") }
        fallback instanceof NativeValueDeserializer
        fallback.unresolved().message == 'none'
    }

    void "a value serde cannot read fails with the registry's reason as the cause and without the value"() {
        given:
        def unresolved = new SerdeException("No deserializer found for type java.lang.Thread")
        parent.findDeserializer(_) >> { throw unresolved }
        def fallback = context.findDeserializer(Argument.of(Thread))
        def decoder = GraphValueDecoder.of([poster: [url: 'secret']])
        def object = decoder.decodeObject()

        when:
        object.decodeKey()
        fallback.deserialize(object, context, Argument.of(Thread, 'poster'))

        then:
        def e = thrown(SerdeException)
        e.cause.is(unresolved)
        e.message == 'Cannot convert a value of type java.util.LinkedHashMap to java.lang.Thread: No deserializer found for type java.lang.Thread (property [poster])'
        !e.message.contains('secret')

        when: "a native value is passed through"
        def point = new Object()
        def value = fallback.deserialize(GraphValueDecoder.of(point), context, Argument.of(Object))

        then:
        value.is(point)

        when: "the decoder is not a graph decoder"
        fallback.deserialize(Mock(io.micronaut.serde.Decoder), context, Argument.of(Thread))

        then:
        e = thrown(SerdeException)
        e.cause.is(unresolved)
        e.message == 'No deserializer for java.lang.Thread: No deserializer found for type java.lang.Thread'
    }

    void "everything else is delegated to the registry context"() {
        when:
        context.findCustomDeserializer(Deserializer)
        context.findNamingStrategy(PropertyNamingStrategy)
        context.getDeserializableSubtypes(Object)
        context.resolveReference(null)
        context.pushManagedRef(null)
        context.popManagedRef()
        context.hasView(String)

        then:
        1 * parent.findCustomDeserializer(Deserializer)
        1 * parent.findNamingStrategy(PropertyNamingStrategy)
        1 * parent.getDeserializableSubtypes(Object)
        1 * parent.resolveReference(null)
        1 * parent.pushManagedRef(null)
        1 * parent.popManagedRef()
        1 * parent.hasView(String)
    }
}
