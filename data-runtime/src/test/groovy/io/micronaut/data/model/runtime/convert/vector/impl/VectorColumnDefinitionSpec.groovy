package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.core.type.Argument
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.inject.annotation.DefaultAnnotationMetadata
import spock.lang.Specification

class VectorColumnDefinitionSpec extends Specification {

    private static Argument<?> arg(Class<?> type, Integer length) {

        if (length == null) {
            return Argument.of(type, "embedding")
        }

        Map<String, Map<CharSequence, Object>> declaredAnnotations = Map.of(
                "jakarta.persistence.Column", Map.of(
                "length", length)
        )

        def metadata = new DefaultAnnotationMetadata(
                declaredAnnotations,               // declaredAnnotations
                Collections.emptyMap(),
                Collections.emptyMap(),
                declaredAnnotations,
                Collections.emptyMap()
        )

        return Argument.of(type, "embedding", metadata)
    }

    void "postgres: vector column definition without and with length"() {
        given:
        def conv = new DefaultVectorAttributeConverter(Collections.emptyList())

        expect:
        conv.getColumnDefinition(arg(DoubleVector, null), DatabaseType.POSTGRES) == "vector"
        conv.getColumnDefinition(arg(DoubleVector, 3), DatabaseType.POSTGRES) == "vector(3)"
        conv.getColumnDefinition(arg(FloatVector, 384), DatabaseType.POSTGRES) == "vector(384)"
    }

    void "mysql: vector column definition without and with length"() {
        given:
        def conv = new DefaultVectorAttributeConverter(Collections.emptyList())

        expect:
        conv.getColumnDefinition(arg(DoubleVector, null), DatabaseType.MYSQL) == "VECTOR"
        conv.getColumnDefinition(arg(DoubleVector, 3), DatabaseType.MYSQL) == "VECTOR(3)"
        conv.getColumnDefinition(arg(FloatVector, 1536), DatabaseType.MYSQL) == "VECTOR(1536)"
    }

    void "oracle: element type inferred from vector subtype and length sets dimension"() {
        expect:
        // DoubleVector - FLOAT64
        new DefaultDoubleVectorAttributeConverter(Collections.emptyList())
                .getColumnDefinition(arg(DoubleVector, null), DatabaseType.ORACLE) == "VECTOR(*,FLOAT64)"
        new DefaultDoubleVectorAttributeConverter(Collections.emptyList())
                .getColumnDefinition(arg(DoubleVector, 3), DatabaseType.ORACLE) == "VECTOR(3,FLOAT64)"

        // FloatVector - FLOAT32
        new DefaultFloatVectorAttributeConverter(Collections.emptyList())
                .getColumnDefinition(arg(FloatVector, null), DatabaseType.ORACLE) == "VECTOR(*,FLOAT32)"
        new DefaultFloatVectorAttributeConverter(Collections.emptyList())
                .getColumnDefinition(arg(FloatVector, 768), DatabaseType.ORACLE) == "VECTOR(768,FLOAT32)"

        // ByteVector - INT8
        new DefaultByteVectorAttributeConverter(Collections.emptyList())
                .getColumnDefinition(arg(ByteVector, null), DatabaseType.ORACLE) == "VECTOR(*,INT8)"
        new DefaultByteVectorAttributeConverter(Collections.emptyList())
                .getColumnDefinition(arg(ByteVector, 128), DatabaseType.ORACLE) == "VECTOR(128,INT8)"
    }
}
