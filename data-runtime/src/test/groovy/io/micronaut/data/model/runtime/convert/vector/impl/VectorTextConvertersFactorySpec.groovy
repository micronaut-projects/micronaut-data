package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.json.JsonMapper
import spock.lang.Specification

class VectorTextConvertersFactorySpec extends Specification {

    void "rejects Oracle sparse vector text with explicit dimensions"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        def jsonMapper = context.getBean(JsonMapper.class)
        VectorTextConvertersFactory factory = new VectorTextConvertersFactory(jsonMapper)

        when:
        factory.fromStringToDoubleVector().convert("[5,[2,4],[10,20]]", DoubleVector, null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid vector JSON text (double[])")

        cleanup:
        context.close()
    }

    void "rejects Oracle sparse vector text without explicit dimensions"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        def jsonMapper = context.getBean(JsonMapper.class)
        VectorTextConvertersFactory factory = new VectorTextConvertersFactory(jsonMapper)

        when:
        factory.fromStringToByteVector().convert("[[2,4],[10,20]]", ByteVector, null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid vector JSON text (byte[])")

        cleanup:
        context.close()
    }

    void "formats sparse Oracle text but does not parse sparse text back"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        def jsonMapper = context.getBean(JsonMapper.class)

        VectorTextConvertersFactory factory = new VectorTextConvertersFactory(jsonMapper)
        Vector sparse = Vector.of([0d, 0d, 3d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 7d] as double[])

        when:
        String encoded = VectorTextFormatter.toText(sparse, true)
        factory.fromStringToVector().convert(encoded, Vector, null)

        then:
        encoded == "[16,[2,15],[3.0,7.0]]"
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid vector JSON text (double[])")

        cleanup:
        context.close()
    }
}
