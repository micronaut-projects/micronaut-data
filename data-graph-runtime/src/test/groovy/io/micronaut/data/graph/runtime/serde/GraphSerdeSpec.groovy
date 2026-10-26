package io.micronaut.data.graph.runtime.serde

import io.micronaut.core.type.Argument
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification

/**
 * Checks how a read that Micronaut Serialization cannot complete is reported, independently of a database.
 */
class GraphSerdeSpec extends Specification {

    Deserializer.DecoderContext registryContext = Mock()
    SerdeRegistry registry = Mock {
        newDecoderContext(_) >> registryContext
    }
    DataConversionService conversionService = Mock {
        convert(_, _ as Argument) >> Optional.empty()
    }
    GraphSerde serde = new GraphSerde(registry, conversionService)

    void "a property serde cannot read fails naming the entity and the property, with the reason as the cause"() {
        given: "an entity deserializer that reads the property [poster] as a type serde has no deserializer for"
        def unresolved = new SerdeException("No deserializer found for type java.lang.Thread")
        Deserializer<Object> movies = { Decoder decoder, Deserializer.DecoderContext context, Argument type ->
            def object = decoder.decodeObject(type)
            object.decodeKey()
            def poster = Argument.of(Thread, 'poster')
            context.findDeserializer(poster).deserialize(object, context, poster)
        } as Deserializer<Object>
        registryContext.findDeserializer(_) >> { Argument argument ->
            if (argument.type == Movie) {
                return movies
            }
            throw unresolved
        }

        when:
        serde.read(Movie, [poster: [url: 'secret']])

        then:
        def e = thrown(DataAccessException)
        e.message == "Cannot read [${Movie.name}] with Micronaut Serialization: Cannot convert a value of type java.util.LinkedHashMap to java.lang.Thread: No deserializer found for type java.lang.Thread (property [poster])"
        !e.message.contains('secret')
        e.cause instanceof SerdeException
        e.cause.cause.is(unresolved)
    }

    static class Movie {
    }
}
