package io.micronaut.data.model.runtime.convert.vector.impl

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.SparseDoubleVector
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.runtime.mapper.ResultReader
import spock.lang.Specification

class VectorAttributeConverterTransformSpec extends Specification {

    @MappedEntity("vector_contract_doc")
    static class VectorContractEntity {
        @Id
        @GeneratedValue
        Long id

        @VectorStorage(length = 5, shape = VectorShape.SPARSE)
        Vector sparseEmbedding

        Vector denseEmbedding
    }

    def "convertToPersistedValue uses converter map for POSTGRES database"() {
        given:
        def delegatingConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> String
            databaseType() >> DatabaseType.POSTGRES
            supportedVectorTypes() >> [Vector]
            convert(_ as Vector) >> { Vector v -> "pg:${v.toDoubleArray().join(',')}" }
        }
        def converter = new DefaultVectorAttributeConverter([delegatingConverter])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }

        when:
        def persisted = converter.convertToPersistedValue(Vector.of(1d, 2d), ctx)

        then:
        persisted == "pg:1.0,2.0"
    }

    def "convertToPersistedValue throws for ORACLE when no converter exists"() {
        given:
        def converter = new DefaultVectorAttributeConverter([])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
        }

        when:
        converter.convertToPersistedValue(Vector.of(1d, 2d), ctx)

        then:
        thrown(IllegalArgumentException)
    }

    def "readFromResultSet delegates to ResultReader with persisted type"() {
        given:
        def delegatingConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> String
            databaseType() >> DatabaseType.POSTGRES
            supportedVectorTypes() >> [Vector]
            convert(_ as Vector) >> { Vector v -> "pg:${v.toDoubleArray().join(',')}" }
            convert(_ as String, _ as Class) >> { Object obj, Class target -> Vector.of(1d, 2d) }
        }
        def converter = new DefaultVectorAttributeConverter([delegatingConverter])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }
        def rr = Stub(ResultReader) {
            getRequiredValue(_, _, _) >> { rs, col, clazz -> "read:${clazz.name}" }
        }

        when:
        def val = converter.readFromResultSet(ctx, rr as ResultReader<Object, Object>, new Object(), "col")

        then:
        val == "read:java.lang.String"
    }

    def "convertToEntityValue delegates to converter for POSTGRES"() {
        given:
        def delegatingConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> String
            databaseType() >> DatabaseType.POSTGRES
            supportedVectorTypes() >> [Vector]
            convert(_ as String, _ as Class) >> { String obj, Class target -> Vector.of(7d, 8d) }
        }
        def converter = new DefaultVectorAttributeConverter([delegatingConverter])
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.POSTGRES
        }

        when:
        def entity = converter.convertToEntityValue("ignored-persisted", ctx)

        then:
        entity instanceof DoubleVector
        entity.toDoubleArray().toList() == [7d, 8d]
    }

    def "generic Vector sparse values are coerced to SparseFloatVector for ORACLE"() {
        given:
        def vectorConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> String
            databaseType() >> DatabaseType.ORACLE
            supportedVectorTypes() >> [Vector]
            convert(_ as Vector) >> { Vector v ->
                assert v instanceof SparseFloatVector
                "oracle:${v.class.simpleName}"
            }
        }
        def converter = new DefaultVectorAttributeConverter([vectorConverter])
        def sparseArg = BeanIntrospection
            .getIntrospection(VectorContractEntity)
            .getRequiredProperty("sparseEmbedding", Vector)
            .asArgument()
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
            getArgument() >> sparseArg
            getAnnotationMetadata() >> sparseArg.getAnnotationMetadata()
        }

        when:
        def persisted = converter.convertToPersistedValue(Vector.of([1d, 0d, 0d, 0d, 0d] as double[]), ctx)

        then:
        persisted == "oracle:SparseFloatVector"
    }

    def "sparse vector written through dense field is materialized to dense with zero fill"() {
        given:
        def vectorConverter = Stub(VectorTypeConverter) {
            getPersistedType() >> String
            databaseType() >> DatabaseType.ORACLE
            supportedVectorTypes() >> [Vector]
            convert(_ as Vector) >> { Vector v ->
                assert v.toDoubleArray().toList() == [0d, 10d, 0d, 20d, 0d]
                "oracle:${v.toDoubleArray().toList()}"
            }
        }
        def converter = new DefaultVectorAttributeConverter([vectorConverter])
        def denseArg = BeanIntrospection
            .getIntrospection(VectorContractEntity)
            .getRequiredProperty("denseEmbedding", Vector)
            .asArgument()
        def ctx = Stub(DatabaseTypeConversionContext) {
            getDatabaseType() >> DatabaseType.ORACLE
            getArgument() >> denseArg
            getAnnotationMetadata() >> denseArg.getAnnotationMetadata()
        }

        when:
        def sparse = new SparseDoubleVector(5, [1, 3] as int[], [10d, 20d] as double[])
        def persisted = converter.convertToPersistedValue(sparse, ctx)

        then:
        persisted == "oracle:[0.0, 10.0, 0.0, 20.0, 0.0]"
    }
}
