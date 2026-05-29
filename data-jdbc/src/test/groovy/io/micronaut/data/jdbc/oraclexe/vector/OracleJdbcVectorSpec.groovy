package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.runtime.convert.DataTypeConverter
import io.micronaut.inject.qualifiers.Qualifiers
import oracle.jdbc.OracleType
import oracle.sql.VECTOR
import spock.lang.Specification

class OracleJdbcVectorSpec extends Specification implements OracleTestPropertyProvider {

    ApplicationContext ctx
    DataTypeConverter<VECTOR, Vector> converter

    void setup() {
        ctx = ApplicationContext.run(properties)
        // Locate the DataTypeConverter<VECTOR, Vector> bean produced by OracleTypeConvertersFactory
        converter = (DataTypeConverter<VECTOR, Vector>) ctx.getBean(
                DataTypeConverter,
                Qualifiers.byTypeArguments(VECTOR, Vector)
        )
    }

    void cleanup() {
        if (ctx != null) {
            ctx.close()
        }
    }

    def "VECTOR_FLOAT32 converts to Vector backed by float[]"() {
        given:
        float[] data = [1.0f, 2.5f, -3.75f] as float[]
        VECTOR v = VECTOR.createVector(data, OracleType.VECTOR_FLOAT32)

        when:
        def result = converter.convert(v, Vector, ConversionContext.of(Argument.of(Vector)))

        then:
        result.present
        def vec = result.get()
        vec.type == Float.TYPE
        vec.toFloatArray().toList() == data.toList()
    }

    def "VECTOR_FLOAT64 converts to Vector backed by double[]"() {
        given:
        double[] data = [1d, 2.5d, -3.75d] as double[]
        VECTOR v = VECTOR.createVector(data, OracleType.VECTOR_FLOAT64)

        when:
        def result = converter.convert(v, Vector, ConversionContext.of(Argument.of(Vector)))

        then:
        result.present
        def vec = result.get()
        vec.type == Double.TYPE
        vec.toDoubleArray().toList() == data.toList()
    }

    def "VECTOR_BINARY converts to Vector backed by byte[] from byte[]"() {
        given:
        byte[] bytes = [1, 127, 0, -1, -128] as byte[]
        VECTOR v = VECTOR.createVector(bytes, OracleType.VECTOR_BINARY)

        when:
        def result = converter.convert(v, Vector, ConversionContext.of(Argument.of(Vector)))

        then:
        result.present
        def vec = result.get()
        vec.type == Byte.TYPE
    }
}
