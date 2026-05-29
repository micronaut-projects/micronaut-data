package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.inject.annotation.DefaultAnnotationMetadata
import spock.lang.Specification

class VectorColumnDefinitionSpec extends Specification {

    private static Argument<?> arg(Class<?> type, Integer length) {

        return arg(type, length, null, null)
    }

    private static Argument<?> arg(Class<?> type, Integer columnLength, Integer vectorStorageLength, Boolean sparse) {

        if (columnLength == null && vectorStorageLength == null && sparse == null) {
            return Argument.of(type, "embedding")
        }

        Map<String, Map<CharSequence, Object>> declaredAnnotations = [:]

        if (columnLength != null) {
            declaredAnnotations.put("jakarta.persistence.Column", Map.of("length", columnLength))
        }

        if (vectorStorageLength != null || sparse != null) {
            Map<CharSequence, Object> vectorStorageAttributes = [:]
            if (vectorStorageLength != null) {
                vectorStorageAttributes.put("length", vectorStorageLength)
            }
            if (sparse != null) {
                vectorStorageAttributes.put("shape", sparse ? VectorShape.SPARSE : VectorShape.DENSE)
            }
            declaredAnnotations.put("io.micronaut.data.annotation.VectorStorage", vectorStorageAttributes)
        }

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
        conv.getColumnDefinition(arg(FloatVector, null, 5, false), DatabaseType.POSTGRES) == "vector(5)"
        conv.getColumnDefinition(arg(FloatVector, 384, 5, false), DatabaseType.POSTGRES) == "vector(5)"
    }

    void "postgres: sparse vector column definition uses sparsevec"() {
        given:
        def conv = new DefaultVectorAttributeConverter(Collections.emptyList())

        expect:
        conv.getColumnDefinition(arg(FloatVector, null, null, true), DatabaseType.POSTGRES) == "sparsevec"
        conv.getColumnDefinition(arg(FloatVector, null, 5, true), DatabaseType.POSTGRES) == "sparsevec(5)"
        conv.getColumnDefinition(arg(FloatVector, 384, 5, true), DatabaseType.POSTGRES) == "sparsevec(5)"
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

    void "oracle: generic Vector with sparse storage defaults to FLOAT32"() {
        given:
        def conv = new DefaultVectorAttributeConverter(Collections.emptyList())

        expect:
        conv.getColumnDefinition(arg(Vector, null, null, true), DatabaseType.ORACLE) == "VECTOR(*,FLOAT32,SPARSE)"
        conv.getColumnDefinition(arg(Vector, null, 5, true), DatabaseType.ORACLE) == "VECTOR(5,FLOAT32,SPARSE)"
    }

}
